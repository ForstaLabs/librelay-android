package io.forsta.librelay.database.model;

public class MessageReceipt {
  private String address;
  private long messageId;
  private boolean delivered;
  private boolean read;
  private boolean failed;

  public MessageReceipt(long messageId, String address, int delivered, int read, int failed) {
    this.messageId = messageId;
    this.address = address;
    this.delivered = delivered != 0 ? true : false;
    this.read = read != 0 ? true : false;
    this.failed = failed != 0 ? true : false;
  }

  public long getMessageId() {
    return messageId;
  }

  public String getAddress() {
    return address;
  }

  public boolean isRead() {
    return read;
  }

  public boolean isDelivered() {
    return delivered;
  }

  public boolean isFailed() {
    return failed;
  }

  @Override
  public String toString() {
    return "Message ID: " + messageId + " Address: " + address + " Delivered: " + delivered + " Read: " + read + " Failed: " + failed;
  }
}
