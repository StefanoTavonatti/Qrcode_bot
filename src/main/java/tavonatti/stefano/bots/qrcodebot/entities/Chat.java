package tavonatti.stefano.bots.qrcodebot.entities;

import tavonatti.stefano.bots.qrcodebot.entities.dao.QRCodeBotDao;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Entity
@NamedQuery(name = "Chat.findAll",query = "SELECT c FROM Chat c")
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

    public static Chat getById(String id){
        EntityManager em= QRCodeBotDao.instance.createEntityManager();
        Chat chat=em.find(Chat.class,id);
        QRCodeBotDao.instance.closeConnections(em);

        return chat;
    }

    public static List<Chat> getAll(){
        EntityManager em=QRCodeBotDao.instance.createEntityManager();
        List<Chat> chats=em.createNamedQuery("Chat.findAll").getResultList();
        QRCodeBotDao.instance.closeConnections(em);

        return chats;
    }

    public static Chat saveChat(Chat c){
        EntityManager em=QRCodeBotDao.instance.createEntityManager();
        EntityTransaction tx=em.getTransaction();
        tx.begin();
        c=em.merge(c);
        tx.commit();
        QRCodeBotDao.instance.closeConnections(em);

        return c;
    }

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
