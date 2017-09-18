package tavonatti.stefano.bots.qrcodebot.entities;

import tavonatti.stefano.bots.qrcodebot.entities.dao.QRCodeBotDao;
import tavonatti.stefano.bots.qrcodebot.entities.extra.Role;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Set;

@Entity
@Table(name = "users")
public class User implements Serializable {
    @Id
    @Column(name="user_id")
    private long userId;

    private String username;

    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToMany(cascade = CascadeType.PERSIST,fetch = FetchType.EAGER, mappedBy = "users")
    private Set<ChatEntity> chatEntities;

    @Column(name = "text_to_emcode")
    private String textToEncode;

    public static User getById(Long id){
        EntityManager em= QRCodeBotDao.instance.createEntityManager();
        User u=em.find(User.class,id);
        QRCodeBotDao.instance.closeConnections(em);

        return u;
    }

    public static User saveUser(User u){
        EntityManager em=QRCodeBotDao.instance.createEntityManager();
        EntityTransaction tx=em.getTransaction();
        tx.begin();
        u=em.merge(u);
        tx.commit();
        QRCodeBotDao.instance.closeConnections(em);

        return u;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<ChatEntity> getChatEntities() {
        return chatEntities;
    }

    public void setChatEntities(Set<ChatEntity> chatEntities) {
        this.chatEntities = chatEntities;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getTextToEncode() {
        return textToEncode;
    }

    public void setTextToEncode(String textToEncode) {
        this.textToEncode = textToEncode;
    }
}
