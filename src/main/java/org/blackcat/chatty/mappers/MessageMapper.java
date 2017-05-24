package org.blackcat.chatty.mappers;

import de.braintags.io.vertx.pojomapper.annotation.Entity;
import de.braintags.io.vertx.pojomapper.annotation.field.Id;
import de.braintags.io.vertx.pojomapper.annotation.field.Referenced;

import java.time.Instant;

@Entity
final public class MessageMapper {
    @Id
    private String id;
    private Instant timeStamp;

    @Referenced
    private UserMapper author;

    @Referenced
    private RoomMapper room;

    private String text;

    public MessageMapper()
    {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Instant timeStamp) {
        this.timeStamp = timeStamp;
    }

    public UserMapper getAuthor() {
        return author;
    }

    public void setAuthor(UserMapper author) {
        this.author = author;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public RoomMapper getRoom() {
        return room;
    }

    public void setRoom(RoomMapper room) {
        this.room = room;
    }

    @Override
    public String toString() {
        return "MessageMapper{" +
                "id='" + id + '\'' +
                ", timeStamp=" + timeStamp +
                ", author=" + author +
                ", room=" + room +
                ", text='" + text + '\'' +
                '}';
    }
}