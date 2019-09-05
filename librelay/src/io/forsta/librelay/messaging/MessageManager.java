package io.forsta.librelay.messaging;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.forsta.librelay.atlas.model.RelayContent;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.util.ForstaUtils;
import io.forsta.librelay.util.InvalidMessagePayloadException;
import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.MediaUtil;

/**
 * Created by jlewis on 10/25/17.
 */

public class MessageManager {
  private static final String TAG = MessageManager.class.getSimpleName();

  public static JSONObject getMessageVersion(int version, String body)
      throws InvalidMessagePayloadException {
    try {
      JSONArray jsonArray = new JSONArray(body);
      for (int i=0; i<jsonArray.length(); i++) {
        JSONObject versionObject = jsonArray.getJSONObject(i);
        if (versionObject.getInt("version") == version) {
          return versionObject;
        }
      }
    } catch (JSONException e) {
      Log.w(TAG, body);
    }
    throw new InvalidMessagePayloadException(body);
  }

  public static RelayContent fromMessagBodyString(String messageBody) throws InvalidMessagePayloadException {
    JSONObject jsonBody = getMessageVersion(1, messageBody);
    return parseMessageBody(jsonBody);
  }

  private static RelayContent parseMessageBody(JSONObject jsonBody) throws InvalidMessagePayloadException {
    RelayContent relayContent = new RelayContent();
    try {
      relayContent.setThreadUid(jsonBody.getString("threadId"));
      if (jsonBody.has("threadTitle") && !jsonBody.isNull("threadTitle")) {
        relayContent.setThreadTitle(jsonBody.getString("threadTitle"));
      }

      if (jsonBody.has("threadType")) {
        relayContent.setThreadType(jsonBody.getString("threadType"));
      }

      if (jsonBody.has("messageType")) {
        relayContent.setMessageType(jsonBody.getString("messageType"));
      }

      // Get sender from Signal envelope and mmsdatabase address field.
      if (!relayContent.isControlMessage()) {
        JSONObject sender = jsonBody.getJSONObject("sender");
        relayContent.setSenderId(sender.getString("userId"));
      }

      relayContent.setMessageId(jsonBody.getString("messageId"));

      JSONObject distribution = jsonBody.getJSONObject("distribution");
      if (distribution.has("expression")) {
        relayContent.setUniversalExpression(distribution.getString("expression"));
      }

      if (jsonBody.has("messageRef")) {
        String messageId = jsonBody.getString("messageRef");
        relayContent.setMessageRef(messageId);
        if (jsonBody.has("vote")) {
          int vote = jsonBody.getInt("vote");
          relayContent.setVote(vote);
        }
      }

      if (jsonBody.has("data")) {
        JSONObject data = jsonBody.getJSONObject("data");
        if (data.has("body")) {
          JSONArray body =  data.getJSONArray("body");
          for (int j=0; j<body.length(); j++) {
            JSONObject object = body.getJSONObject(j);
            if (object.getString("type").equals("text/html")) {
              relayContent.setHtmlBody(object.getString("value"));
            }
            if (object.getString("type").equals("text/plain")) {
              relayContent.setTextBody(object.getString("value"));
            }
          }
        } else {
          if (data.has("vote")) {
            relayContent.setVote(data.getInt("vote"));
          } else {
            // Temporary. Fixes missing vote field in data
            if (jsonBody.has("messageRef")) {
              relayContent.setVote(1);
            }
          }
        }
        if (data.has("attachments")) {
          JSONArray attachments = data.getJSONArray("attachments");
          for (int i=0; i<attachments.length(); i++) {
            JSONObject object = attachments.getJSONObject(i);
            String name = object.getString("name");
            String type = object.getString("type");
            long size = object.getLong("size");
            relayContent.addAttachment(name, type, size);
          }
        }
        if (data.has("mentions")) {
          JSONArray mentions = data.getJSONArray(("mentions"));
          for (int i = 0; i < mentions.length(); i++) {
            String id = mentions.getString(i);
            relayContent.addMention(id);
          }
        }

        if (data.has("control")) {
          relayContent.setControlType(data.getString("control"));

          switch (relayContent.getControlType()) {
            case RelayContent.ControlTypes.THREAD_UPDATE:
              if (TextUtils.isEmpty(relayContent.getUniversalExpression())) {
                throw new InvalidMessagePayloadException("Thread update. No universal expression.");
              }
              JSONObject threadUpdates = data.getJSONObject("threadUpdates");
              if (threadUpdates.has("threadTitle")) {
                relayContent.setThreadTitle(threadUpdates.getString("threadTitle"));
              }
              break;
            case RelayContent.ControlTypes.READ_MARK:
              try {
                long timeStamp = data.getLong("readMark");
                Log.w(TAG, "Read Mark: " + timeStamp);
                relayContent.setReadMark(timeStamp);
              } catch (Exception e) {
                Log.w(TAG, e.getMessage());
              }
              break;
            case RelayContent.ControlTypes.PROVISION_REQUEST:
              String uuid = data.getString("uuid");
              String key = data.getString("key");
              relayContent.setProvisionRequest(uuid, key);
              break;
            case RelayContent.ControlTypes.CALL_OFFER:
              if (data.has("offer")) {
                String originator = data.optString("originator");
                String callId = data.getString("callId");
                JSONObject offer = data.getJSONObject("offer");
                String spd = offer.optString("sdp");
                String peerId = data.getString("peerId");
                relayContent.setCallOffer(callId, originator, peerId, spd);

                if (data.has("members")) {
                  JSONArray callMembers = data.getJSONArray("members");
                  RelayContent.ForstaCall currentCall = relayContent.getCall();
                  for (int i=0; i<callMembers.length(); i++) {
                    String memberId = callMembers.getString(i);
                    currentCall.addCallMember(memberId);
                  }
                }
              } else {
                Log.w(TAG, "Not a valid callOffer control message");
              }
              break;
            case RelayContent.ControlTypes.CALL_ACCEPT_OFFER:
              if (data.has("answer")) {
                String originator = data.optString("originator");
                String callId = data.getString("callId");
                JSONObject answer = data.getJSONObject("answer");
                String spd = answer.optString("sdp");
                String peerId = data.getString("peerId");
                relayContent.setCallOffer(callId, originator, peerId, spd);
              } else {
                Log.w(TAG, "Not a valid callAcceptOffer control message");
              }
              break;

            case RelayContent.ControlTypes.CALL_JOIN:
              String joinOriginator = data.getString("originator");
              String joinCallId = data.getString("callId");
              List<String> members = new ArrayList<>();
              if (data.has("members")) {
                JSONArray callMembers = data.getJSONArray("members");
                for (int i=0; i<callMembers.length(); i++) {
                  String memberId = callMembers.getString(i);
                  members.add(memberId);
                }
              }
              relayContent.setCallJoin(joinCallId, joinOriginator, members);
              break;
            case RelayContent.ControlTypes.CALL_ICE_CANDIDATES:
              if (data.has("icecandidates")) {
                String originator = data.optString("originator");
                String callId = data.getString("callId");
                String peerId = data.getString("peerId");
                JSONArray callIceCandidates = data.getJSONArray("icecandidates");
                List<IceCandidate> candidates = new ArrayList<>();
                for (int i=0; i<callIceCandidates.length(); i++) {
                  JSONObject iceCandidate = callIceCandidates.getJSONObject(i);
                  String spdMid = iceCandidate.getString("sdpMid");
                  int spdLineIndex = iceCandidate.getInt("sdpMLineIndex");
                  String spd = iceCandidate.getString("candidate");
                  candidates.add(new IceCandidate(spdMid, spdLineIndex, spd));
                }
                relayContent.setIceCandidates(callId, originator, peerId, candidates);
              } else {
                Log.w(TAG, "Not a valid callIceCandidate control message");
              }
              break;
            case RelayContent.ControlTypes.CALL_LEAVE:
              String originator = data.optString("originator");
              String callId = data.getString("callId");
              relayContent.setCallLeave(callId, originator);
              break;
            case RelayContent.ControlTypes.CLOSE_SESSION:
              Log.w(TAG, "Received close session control");
              break;
            default:
              Log.w(TAG, "Unsupported control message: " + relayContent.getControlType());
          }
        }
      }

      if (!relayContent.isControlMessage()) {
        if (TextUtils.isEmpty(relayContent.getUniversalExpression())) {
          throw new InvalidMessagePayloadException("Content message. No universal expression.");
        }
      }

    } catch (JSONException e) {
      Log.e(TAG, jsonBody.toString());
      throw new InvalidMessagePayloadException(e.getMessage());
    }

    return relayContent;
  }

  public static String createCallLeaveMessage(AtlasUser user, ThreadRecord threadRecord, String callId) {
    JSONObject data = new JSONObject();
    try {
      data.put("control", "callLeave");
      data.put("callId", callId);
      data.put("originator", user.getUid());
      data.put("version", 2);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return createBaseMessageBody(user, threadRecord, RelayContent.MessageTypes.CONTROL, data);
  }

  public static String createAcceptCallOfferMessage(AtlasUser user, ThreadRecord threadRecord, String callId, String description, String peerId) {
    JSONObject data = new JSONObject();
    try {
      data.put("control", "callAcceptOffer");
      data.put("peerId", peerId);
      data.put("callId", callId);
      data.put("originator", user.getUid());
      JSONObject answer = new JSONObject();
      answer.put("sdp", description);
      answer.put("type", "answer");
      data.put("answer", answer);
      data.put("version", 2);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return createBaseMessageBody(user, threadRecord, RelayContent.MessageTypes.CONTROL, data);
  }

  public static String createCallOfferMessage(AtlasUser user, ThreadRecord threadRecord, String callId, String description, String peerId) {
    JSONObject data = new JSONObject();
    try {
      data.put("control", "callOffer");
      data.put("callId", callId);
      data.put("originator", user.getUid());
      JSONObject offer = new JSONObject();
      offer.put("sdp", description);
      offer.put("type", "offer");
      data.put("offer", offer);
      data.put("peerId", peerId);
      data.put("version", 2);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return createBaseMessageBody(user, threadRecord, RelayContent.MessageTypes.CONTROL, data);
  }

  public static String createCallJoinMessage(AtlasUser user, List<String> memberAddresses, ThreadRecord threadRecord, String callId, String peerId) {
    Recipients recipients = threadRecord.getRecipients();
    JSONObject data = new JSONObject();
    try {
      data.put("control", "callJoin");
      JSONArray members = new JSONArray();
      for (String x : memberAddresses) {
        members.put(x);
      }
      if (recipients.isSingleRecipient()) {
        members.put(user.getUid());
      }
      data.put("members", members);
      data.put("callId", callId);
      data.put("originator", user.getUid());
      JSONObject offer = new JSONObject();
      data.put("peerId", peerId);
      data.put("version", 2);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return createBaseMessageBody(user, threadRecord, RelayContent.MessageTypes.CONTROL, data);
  }

  public static String createThreadUpdateMessage(Context context, AtlasUser user, ThreadRecord threadRecord) {
    JSONObject data = new JSONObject();
    try {
      data.put("control", "threadUpdate");
      JSONObject threadUpdates = new JSONObject();
      threadUpdates.put("threadTitle", threadRecord.getTitle());
      threadUpdates.put("threadId", threadRecord.getUid());
      data.put("threadUpdates", threadUpdates);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return createBaseMessageBody(user, threadRecord, RelayContent.MessageTypes.CONTROL, data);
  }

  public static String createIceCandidateMessage(AtlasUser user, ThreadRecord threadRecord, String callId, String peerId, List<IceCandidate> candidates) {
    JSONObject data = new JSONObject();
    try {
      JSONArray jsonUpdates = new JSONArray();
      for (IceCandidate candidate : candidates) {
        JSONObject jsonCandidate = new JSONObject();
        jsonCandidate.put("candidate", candidate.sdp);
        jsonCandidate.put("sdpMid", candidate.sdpMid);
        jsonCandidate.put("sdpMLineIndex", candidate.sdpMLineIndex);
        jsonUpdates.put(jsonCandidate);
      }

      data.put("control", "callICECandidates");
      data.put("icecandidates", jsonUpdates);
      data.put("peerId", peerId);
      data.put("callId", callId);
      data.put("originator", user.getUid());
      data.put("version", 2);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return createBaseMessageBody(user, threadRecord, RelayContent.MessageTypes.CONTROL, data);
  }

  private static String createBaseMessageBody(AtlasUser user, ThreadRecord threadRecord, String type, JSONObject data) {
    return createBaseMessageBody(user, threadRecord, type, data, null, null);
  }

  private static String createBaseMessageBody(AtlasUser user, ThreadRecord threadRecord, String type, JSONObject data, String messageUid, String messageRef) {
    Recipients messageRecipients = threadRecord.getRecipients();
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    String title = threadRecord.getTitle();
    try {
      String messageType = "content";
      if (type.equals(RelayContent.MessageTypes.CONTROL)) {
        messageType = "control";
      }

      String threadType = threadRecord.getThreadType() == 1 ? "announcement" : "conversation";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray userIds = new JSONArray();

      String threadId = !TextUtils.isEmpty(threadRecord.getUid()) ? threadRecord.getUid() : "";
      sender.put("tagId", user.tag_id);
      sender.put("tagPresentation", user.slug);
      sender.put("userId", user.uid);

      for (Recipient x : messageRecipients) {
        userIds.put(x.getAddress());
      }

      if (!TextUtils.isEmpty(messageRef)) {
        version1.put("messageRef", messageRef);
      }
      recipients.put("userIds", userIds);
      recipients.put("expression", threadRecord.getDistribution());
      version1.put("version", 1);
      version1.put("userAgent", System.getProperty("http.agent", ""));
      version1.put("messageId", !TextUtils.isEmpty(messageUid) ? messageUid : UUID.randomUUID().toString());
      version1.put("messageType", messageType);
      version1.put("threadId", threadId);
      version1.put("threadTitle", title);
      version1.put("threadType", threadType);
      version1.put("sendTime", ForstaUtils.formatDateISOUTC(new Date()));
      version1.put("sender", sender);
      version1.put("distribution", recipients);
      version1.put("data", data);
      versions.put(version1);
    } catch (JSONException e) {
      Log.e(TAG, "createForstaMessageBody JSON exception");
      Log.e(TAG, "Thread: "+ threadRecord.getUid());
      Log.e(TAG, data.toString());
      e.printStackTrace();
    }
    return versions.toString();
  }

  private static String createContentReplyMessage(Context context, String message, AtlasUser user, List<Attachment> messageAttachments, ThreadRecord thread, String messageUid, String messageRef, int vote) {
    return createContentMessage(context, message, user, messageAttachments, thread, messageUid, messageRef, vote);
  }

  private static String createContentMessage(Context context, String message, AtlasUser user, List<Attachment> messageAttachments, ThreadRecord thread, String messageUid) {
    return createContentMessage(context, message, user, messageAttachments, thread, messageUid, null, 0);
  }

  private static String createContentMessage(Context context, String message, AtlasUser user, List<Attachment> messageAttachments, ThreadRecord thread, String messageUid, String messageRef, int vote) {
    Recipients recipients = thread.getRecipients();
    JSONObject data = new JSONObject();
    JSONArray body = new JSONArray();
    JSONArray mentions = new JSONArray();
    JSONArray attachments = new JSONArray();
    try {
      if (attachments != null) {
        for (Attachment attachment : messageAttachments) {
          JSONObject attachmentJson = new JSONObject();
          attachmentJson.put("name", MediaUtil.getFileName(context, attachment.getDataUri()));
          attachmentJson.put("size", attachment.getSize());
          attachmentJson.put("type", attachment.getContentType());
          attachments.put(attachmentJson);
        }
      }

      AtlasUser parsedUser;
      String tagRegex = "@[a-zA-Z0-9(-|.)]+";
      Pattern tagPattern = Pattern.compile(tagRegex);
      Matcher tagMatcher = tagPattern.matcher(message);
      while (tagMatcher.find()) {
        String parsedTag = message.substring(tagMatcher.start(), tagMatcher.end());
        Recipient recipient = DbFactory.getContacts(context).getRecipientByTag(parsedTag.replace("@", ""));
        if(recipient != null) {
          mentions.put(recipient.getAddress());
        }
      }

      JSONObject bodyHtml = new JSONObject();
      bodyHtml.put("type", "text/html");
      bodyHtml.put("value", message);
      body.put(bodyHtml);

      JSONObject bodyPlain = new JSONObject();
      bodyPlain.put("type", "text/plain");
      Spanned stripMarkup = Html.fromHtml(message);
      bodyPlain.put("value", stripMarkup);
      body.put(bodyPlain);

      if (!TextUtils.isEmpty(messageRef) && vote != 0) {
        data.put("vote", vote);
      }
      data.put("body", body);
      data.put("attachments", attachments);
      if (mentions.length() > 0) {
        data.put("mentions", mentions );
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return createBaseMessageBody(user, thread, RelayContent.MessageTypes.CONTENT, data, messageUid, messageRef);
  }

  public static String createForstaMessageBody(Context context, String message, List<Attachment> messageAttachments, ThreadRecord threadRecord) {
    Recipients recipients = threadRecord.getRecipients();
    AtlasUser user = AtlasUser.getLocalUser(context);
    return createContentMessage(context, message, user, messageAttachments, threadRecord, UUID.randomUUID().toString());
  }

  public static OutgoingMediaMessage createOutgoingContentMessage(Context context, String message, List<Attachment> attachments, long threadId, long expiresIn) {
    ThreadRecord thread = DbFactory.getThreadDatabase(context).getThread(threadId);
    Recipients recipients = thread.getRecipients();
    AtlasUser user = AtlasUser.getLocalUser(context);
    String uid = UUID.randomUUID().toString();
    String jsonPayload = createContentMessage(context, message, user, attachments, thread, uid);
    return new OutgoingMediaMessage(recipients, jsonPayload, attachments, System.currentTimeMillis(), expiresIn, uid, null, 0);
  }

  public static OutgoingMediaMessage createOutgoingContentReplyMessage(Context context, String message, List<Attachment> attachments, long threadId, long expiresIn, String messageRef, int vote) {
    ThreadRecord thread = DbFactory.getThreadDatabase(context).getThread(threadId);
    Recipients recipients = thread.getRecipients();
    AtlasUser user = AtlasUser.getLocalUser(context);
    String uid = UUID.randomUUID().toString();
    String jsonPayload = createContentReplyMessage(context, message, user, attachments, thread, uid, messageRef, vote);
    return new OutgoingMediaMessage(recipients, jsonPayload, attachments, System.currentTimeMillis(), expiresIn, uid, messageRef, vote);
  }

  public static OutgoingExpirationUpdateMessage createOutgoingExpirationUpdateMessage(Context context, long threadId, long expiresIn) {
    ThreadRecord thread = DbFactory.getThreadDatabase(context).getThread(threadId);
    Recipients recipients = thread.getRecipients();
    AtlasUser user = AtlasUser.getLocalUser(context);
    String uid = UUID.randomUUID().toString();
    String jsonPayload = createContentMessage(context, "", user, new LinkedList<Attachment>(), thread, uid);
    return new OutgoingExpirationUpdateMessage(recipients, jsonPayload, System.currentTimeMillis(), expiresIn);
  }

  public static OutgoingEndSessionMediaMessage createOutgoingEndSessionMessage(Context context, long threadId) {
    ThreadRecord thread = DbFactory.getThreadDatabase(context).getThread(threadId);
    Recipients recipients = thread.getRecipients();
    AtlasUser user = AtlasUser.getLocalUser(context);
    JSONObject data = new JSONObject();
    try {
      data.put("control", "closeSession");
      JSONArray retransmitTimeStamps = new JSONArray();
      // retransmits: [<number>] // Used with `closeSession` control to indicate which messages (sent timestamp) should be resent due to decryption error.  Client should validate veracity of requester before complying.
      if (retransmitTimeStamps.length() > 0) {
        data.put("retransmits", retransmitTimeStamps);
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    String jsonPayload = createBaseMessageBody(user, thread, RelayContent.MessageTypes.CONTROL, data);
    return new OutgoingEndSessionMediaMessage(recipients, jsonPayload, System.currentTimeMillis());
  }

  public static IncomingMediaMessage createLocalInformationalMessage(Context context, String message, long threadId, long expiresIn) {
    ThreadRecord thread = DbFactory.getThreadDatabase(context).getThread(threadId);
    AtlasUser user = AtlasUser.getLocalUser(context);
    String uid = UUID.randomUUID().toString();
    String jsonPayload = createContentMessage(context, message, user, new ArrayList<Attachment>(), thread, uid);
    IncomingMediaMessage incomingMessage = new IncomingMediaMessage(user.getUid(), user.getUid(), jsonPayload, System.currentTimeMillis(), expiresIn);
    return incomingMessage;
  }
}
