package org.blackcat.chatty.mappers;


import de.braintags.vertx.jomnigate.annotation.Entity;
import de.braintags.vertx.jomnigate.annotation.field.Id;
import de.braintags.vertx.jomnigate.annotation.field.Referenced;

@Entity
final public class MessageMapper {
    @Id
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    private String timeStamp;
    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    @Referenced
    private UserMapper author;

    public UserMapper getAuthor() {
        return author;
    }

    public void setAuthor(UserMapper author) {
        this.author = author;
    }

    @Referenced
    private RoomMapper room;
    public RoomMapper getRoom() {
        return room;
    }

    public void setRoom(RoomMapper room) {
        this.room = room;
    }
    private String text;

    public MessageMapper()
    {}

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "MessageMapper{" +
                "uuid='" + uuid + '\'' +
                ", timeStamp='" + timeStamp + '\'' +
                ", author=" + author +
                ", room=" + room +
                ", text='" + text + '\'' +
                '}';
    }
}