package tavonatti.stefano.bots.qrcodebot.entities;

import tavonatti.stefano.bots.qrcodebot.entities.dao.QRCodeBotDao;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Entity
@NamedQuery(name = "ChatEntity.findAll",query = "SELECT c FROM ChatEntity c")
@Table(name = "chats")
public class ChatEntity implements Serializable{

    @Id
    @Column(name = "chat_id")
    private long chatId;

    @ManyToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    private Set<User> users;

    @Column(name = "number_of_uses")
    private Long numberOfUses;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_use")
    private Date lasUse;

    public static ChatEntity getById(long id){
        EntityManager em= QRCodeBotDao.instance.createEntityManager();
        ChatEntity chatEntity =em.find(ChatEntity.class,id);
        QRCodeBotDao.instance.closeConnections(em);

        return chatEntity;
    }

    public static List<ChatEntity> getAll(){
        EntityManager em=QRCodeBotDao.instance.createEntityManager();
        List<ChatEntity> chatEntities =em.createNamedQuery("ChatEntity.findAll").getResultList();
        QRCodeBotDao.instance.closeConnections(em);

        return chatEntities;
    }

    public static ChatEntity saveChat(ChatEntity c){
        EntityManager em=QRCodeBotDao.instance.createEntityManager();
        EntityTransaction tx=em.getTransaction();
        tx.begin();
        c=em.merge(c);
        tx.commit();
        QRCodeBotDao.instance.closeConnections(em);

        return c;
    }


    public static List<ChatEntity> getAllByDate(){
        EntityManager em=QRCodeBotDao.instance.createEntityManager();
        TypedQuery<ChatEntity> query=em.createQuery("SELECT DISTINCT c FROM ChatEntity c " +
                "ORDER By c.lasUse DESC ",ChatEntity.class);
        List<ChatEntity> chats=query.getResultList();
        QRCodeBotDao.instance.closeConnections(em);
        return chats;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    public Long getNumberOfUses() {
        return numberOfUses;
    }

    public void setNumberOfUses(Long numberOfUses) {
        this.numberOfUses = numberOfUses;
    }

    public Date getLasUse() {
        return lasUse;
    }

    public void setLasUse(Date lasUse) {
        this.lasUse = lasUse;
    }
}
