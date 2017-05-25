package org.blackcat.chatty.mappers;

import de.braintags.io.vertx.pojomapper.annotation.Entity;
import de.braintags.io.vertx.pojomapper.annotation.field.Id;

@Entity
final public class UserMapper {
    @Id
    private String uuid;
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    private String email;
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserMapper()
    {}

    @Override
    public String toString() {
        return "UserMapper{" +
                "uuid='" + uuid + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}