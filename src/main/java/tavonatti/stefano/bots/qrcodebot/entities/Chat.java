package tavonatti.stefano.bots.qrcodebot.entities;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;

@Entity
public class Chat implements Serializable{

    @Id
    @Column(name = "chat_id")
    private String chatId;

    @ManyToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    private Set<User> users;

    @Column(name = "number_of_uses")
    private long numberOfUses;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_use")
    private Date lasUse;

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    public long getNumberOfUses() {
        return numberOfUses;
    }

    public void setNumberOfUses(long numberOfUses) {
        this.numberOfUses = numberOfUses;
    }

    public Date getLasUse() {
        return lasUse;
    }

    public void setLasUse(Date lasUse) {
        this.lasUse = lasUse;
    }
}
