package org.blackcat.chatty.mappers;

import de.braintags.io.vertx.pojomapper.annotation.Entity;
import de.braintags.io.vertx.pojomapper.annotation.field.Embedded;
import de.braintags.io.vertx.pojomapper.annotation.field.Id;

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

    @Embedded
    private UserMapper author;

    public UserMapper getAuthor() {
        return author;
    }

    public void setAuthor(UserMapper author) {
        this.author = author;
    }

    @Embedded
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