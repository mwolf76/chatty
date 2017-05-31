package org.blackcat.chatty.mappers;

import de.braintags.vertx.jomnigate.annotation.Entity;
import de.braintags.vertx.jomnigate.annotation.field.Id;
import de.braintags.vertx.jomnigate.annotation.field.Referenced;

@Entity
final public class RoomMapper {
    @Id
    private String uuid;
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    private String name;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Referenced
    UserMapper creator;

    public UserMapper getCreator() {
        return creator;
    }

    public void setCreator(UserMapper creator) {
        this.creator = creator;
    }

    public RoomMapper()
    {}

    @Override
    public String toString() {
        return "RoomMapper{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", creator=" + creator +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RoomMapper that = (RoomMapper) o;

        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}