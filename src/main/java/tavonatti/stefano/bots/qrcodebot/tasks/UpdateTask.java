package tavonatti.stefano.bots.qrcodebot.tasks;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.vdurmont.emoji.EmojiParser;
import org.apache.log4j.Logger;
import org.telegram.telegrambots.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.*;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.api.objects.File;
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.api.objects.inlinequery.result.chached.InlineQueryResultCachedPhoto;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tavonatti.stefano.bots.qrcodebot.QrCodeBot;
import tavonatti.stefano.bots.qrcodebot.entities.ChatEntity;
import tavonatti.stefano.bots.qrcodebot.entities.User;
import tavonatti.stefano.bots.qrcodebot.entities.extra.Role;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class UpdateTask implements Runnable {

    public static Logger logger=Logger.getLogger(UpdateTask.class);

    private final static String CHARSET="UTF-8"; // or "ISO-8859-1"

    private final static int WIDTH_QR=500;
    private final static int HEIGHT_QR=500;
    private final static int SIZE_THRESHOLD=250;

    private final static long MAX_USE_NUM=9223372036854775000L;
    private final static String INLINE_IMG_TOCKEN="###";

    private final static int MAX_FILE_SIZE=1024*20;//TODO define

    private final static int WIDTH_DM=64;
    private final static int HEIGHT_DM=64;

    private QrCodeBot qrCodeBot;
    private Update update;

    private final static boolean VCARD_ENABLED=true;

    public UpdateTask(QrCodeBot qrCodeBot, Update update){
        this.qrCodeBot=qrCodeBot;
        this.update=update;
    }

    public void run() {

        if(update.hasMessage() && update.getMessage().hasText()){
            SendMessage message = new SendMessage() // Create a SendMessage object with mandatory fields
                    .setChatId(update.getMessage().getChatId());

            if(logger.isInfoEnabled()) {
                logger.info("user name: "+update.getMessage().getFrom().getUserName());
                logger.info("user id:"+update.getMessage().getFrom().getId());
                logger.info("message id:"+update.getMessage().getMessageId());
                logger.info(update.getMessage().getText());
            }

            updateChatAndUserInformation();

            String[] splits=update.getMessage().getText().split(" ");

            String[] commandSplit=splits[0].split("@");

            if(commandSplit.length>1){
                if(!commandSplit[1].equals(qrCodeBot.getBotUsername())){
                    return;
                }
            }

            switch (commandSplit[0]){
                case "/start":
                    if(splits.length>1){
                        /*for inline mode
                        * encode the QR and forward to original chat*/
                        User u=User.getById(Long.valueOf(update.getMessage().getFrom().getId()));

                        if(u==null){
                            sendHelpMessage();
                            return;
                        }

                        SendMessage waitMessage=new SendMessage();

                        waitMessage.setChatId(update.getMessage().getChatId());
                        waitMessage.setText("encoding your text...");

                        qrCodeBot.sendResponse(waitMessage);


                        SendPhoto sendPhoto=new SendPhoto();
                        sendPhoto.setChatId(update.getMessage().getChatId());

                        InputStream is=getQRInputStream(u.getTextToEncode());

                        sendPhoto.setNewPhoto("qrcode",is);

                        InlineKeyboardMarkup inlineKeyboardMarkup=new InlineKeyboardMarkup();

                        List<List<InlineKeyboardButton>> rows=new ArrayList<>();

                        List<InlineKeyboardButton> row=new ArrayList<>();

                        rows.add(row);

                        InlineKeyboardButton inlineKeyboardButton=new InlineKeyboardButton();

                        inlineKeyboardButton.setText("send qr");

                        row.add(inlineKeyboardButton);

                        inlineKeyboardMarkup.setKeyboard(rows);

                        //sendPhoto.setReplyMarkup(inlineKeyboardMarkup);

                        Message photoSent;

                        try {
                            photoSent=qrCodeBot.sendPhoto(sendPhoto);
                        } catch (TelegramApiException e) {
                            sendErrorMessage("unable to create qr");//TODO logging in questo metodo
                            e.printStackTrace();
                            return;
                        }

                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        inlineKeyboardButton.setSwitchInlineQuery(INLINE_IMG_TOCKEN);

                        String f_id = photoSent.getPhoto().stream()
                                .sorted(Comparator.comparing(PhotoSize::getFileSize).reversed())
                                .findFirst()
                                .orElse(null).getFileId();

                        u.setFileIdForInline(f_id);
                        u.setThumbIdForInline(String.valueOf(photoSent.getPhoto().get(0).getFileId()));

                        User.saveUser(u);

                        SendMessage message1=new SendMessage();
                        message1.setChatId(update.getMessage().getChatId());
                        message1.setText("OK");

                        message1.setReplyMarkup(inlineKeyboardMarkup);

                        qrCodeBot.sendResponse(message1);

                        return;
                    }
                    //break;
                case "/instruction":
                case "/help":
                    sendHelpMessage();
                    break;
                case "/encode":
                    if(splits.length<2){
                        message.setText("use:\n/encode <text>");
                        qrCodeBot.sendResponse(message);
                        break;
                    }

                    String text="";

                    //i=1 in order to skyp the encode command
                    for(int i=1;i<splits.length;i++){
                        if(i!=1){
                            text+=" ";
                        }
                        text+=splits[i];
                    }

                    SendPhoto sendPhoto=new SendPhoto();
                    sendPhoto.setChatId(update.getMessage().getChatId());

                    /*create Qrcode */
                    InputStream is = getQRInputStream(text);
                    if (is == null) return;

                    /*send the qrcode*/
                    sendPhoto.setNewPhoto("qrcode",is);

                    try {
                        qrCodeBot.sendPhoto(sendPhoto);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }

                    return;

                case "/encode_wifi":

                    /* create a QR for wifi credential*/
                    // wifi connection string WIFI:S:<SSID>;T:<WPA|WEP|>;P:<password>;;
                    SendPhoto sendPhotoWIFI=new SendPhoto();
                    sendPhotoWIFI.setChatId(update.getMessage().getChatId());

                    String ssid,type,password;

                    if(splits.length==4){
                        ssid=splits[1];
                        type=splits[2];
                        password=splits[3];
                    } else if (splits.length==3){
                        ssid=splits[1];
                        password=splits[2];
                        type="WPA";
                    }
                    else {
                        message.setText("use:\n/encode_wifi <SSID> <WPA|WEP> <password> " +
                                "\nor: /encode_wifi <SSID> <password> for WPA network");
                        qrCodeBot.sendResponse(message);
                        break;
                    }

                    /*create Qrcode */
                    InputStream isWIFI = getQRInputStream("WIFI:S:"+ssid+";T:"+type+";P:"+password+";;");
                    if (isWIFI == null) return;

                    /*send the qrcode*/
                    sendPhotoWIFI.setNewPhoto("qrcode_WIFI",isWIFI);

                    try {
                        qrCodeBot.sendPhoto(sendPhotoWIFI);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }

                    return;
                case "/encode_dm":

                    if(splits.length<2){
                        message.setText("use:\n/encode_dm <text>");
                        qrCodeBot.sendResponse(message);
                        break;
                    }

                    String textDM="";

                    //i=1 in order to skyp the encode command
                    for(int i=1;i<splits.length;i++){
                        if(i!=1){
                            textDM+=" ";
                        }
                        textDM+=splits[i];
                    }

                    SendPhoto sendPhotoDM=new SendPhoto();
                    sendPhotoDM.setChatId(update.getMessage().getChatId());

                    InputStream isDM = null;

                    try {
                        isDM=getInputStreamFromBufferedImage(createDataMatrix(textDM));
                    } catch (WriterException e) {
                        e.printStackTrace();
                        sendErrorMessage("Unable to encode the text");
                    }

                    if(isDM==null)
                        return;

                    sendPhotoDM.setNewPhoto("DataMatrix",isDM);

                    try {
                        qrCodeBot.sendPhoto(sendPhotoDM);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }

                    return;
                case "/chats":

                    User u=User.getById(Long.valueOf(update.getMessage().getFrom().getId()));

                    if(u==null){
                        return;
                    }

                    if(u.getRole()==null){
                        return;
                    }

                    if(!u.getRole().equals(Role.ADMIN)){
                        return;
                    }

                    List<ChatEntity> chatEntities=ChatEntity.getAllByDate();
                    String chatsString="Last used chat\n";

                    Iterator<ChatEntity> it=chatEntities.iterator();

                    while (it.hasNext()){
                        ChatEntity c=it.next();

                        Iterator<User> it1=c.getUsers().iterator();
                        while (it1.hasNext()){
                            User user=it1.next();
                            chatsString+=user.getUsername()+"\t*"+c.getNumberOfUses()+"*\t"+c.getLasUse()+"\t"+
                                    c.getChatId()+"\n";
                        }
                    }

                    chatsString=chatsString.replace("_","\\_");
                    if(chatsString.length()>4000){
                        chatsString=chatsString.substring(0,4000)+"\n...TRUNCATED";
                    }

                    message.setText(chatsString);
                    message.enableMarkdown(true);

                    qrCodeBot.sendResponse(message);

                    return;

                default:
                    if(update.getMessage().isUserMessage()) {
                        message.setText("Type /help for the list of available commands");
                        qrCodeBot.sendResponse(message);
                    }
                    break;
            }


        }
        else if(update.hasMessage() && update.getMessage().hasPhoto()){
            List<PhotoSize> photoSizes=update.getMessage().getPhoto();

            logger.info("user name: "+update.getMessage().getFrom().getUserName());
            logger.info("user id:"+update.getMessage().getFrom().getId());
            logger.info("photo");

            updateChatAndUserInformation();

            if(photoSizes.size()<=0){

                String text="Unable to receive the file";

                sendErrorMessage(text);

                return;
            }

            String f_id = photoSizes.stream()
                    .sorted(Comparator.comparing(PhotoSize::getFileSize).reversed())
                    .findFirst()
                    .orElse(null).getFileId();

            //logger.info("file size <PhotoSize>: "+max);

            GetFile getFileRequest = new GetFile();
            getFileRequest.setFileId(f_id);

            File file=null;

            try {
                file=qrCodeBot.getFile(getFileRequest);
            } catch (TelegramApiException e) {
                logger.error("file not exits");
                sendErrorMessage("File not exits");
                e.printStackTrace();
            }

            logger.info("file url: "+file.getFileUrl(qrCodeBot.getBotToken()));
            logger.info(file.toString());

            InputStream is=null;

            try {
                is=getFileInputStream(file);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                sendErrorMessage("Unable to download file");
                return;
            } catch (IOException e) {
                e.printStackTrace();
                sendErrorMessage("Unable to download file");
                return;
            }

            Map hintMap = getHintMap();

            SendMessage message=new SendMessage();
            message.setChatId(update.getMessage().getChatId());

            try {

                 /* Vcart example

                BEGIN:VCARD
                VERSION:3.0
                FN:Paolo Rossi
                ADR:;;123 Street;City;Region;PostalCode;Country
                TEL:+908888888888
                TEL:+901111111111
                TEL:+902222222222
                EMAIL;TYPE=home:homeemail@example.com
                EMAIL;TYPE=work:workemail@example.com
                URL:http://www.google.com
                END:VCARD
                 */

                String text=readQRCode(is,hintMap);

                /* if the QRcode contains a vCard, send the contac*/
                if(text.startsWith("BEGIN:VCARD")){

                    if (decodeAndSendContact(message, text)) return;

                }
                else if(text.startsWith("geo:")|| text.startsWith("GEO:")){
                    if (decodeAndSendLocation(text)) return;

                    return;

                }
                else {
                    message.setText(text);
                }
            } catch (IOException e) {
                e.printStackTrace();
                sendErrorMessage("Unable to download file");
                return;
            } catch (NotFoundException e) {
                logger.error("No qrcode");
                sendErrorMessage("Unable to find a QRcode in the image");
                return;
            }

            qrCodeBot.sendResponse(message);

        }
        else if(update.hasInlineQuery() && qrCodeBot.dbLoggingEnabled()){
            /*the inline query mode is available only with the dblogging enable*/
            logger.info("Inline query");

            if(!update.getInlineQuery().hasQuery())
                return;

            logger.info("User name "+update.getInlineQuery().getFrom().getUserName());
            logger.info("Query: "+update.getInlineQuery().getQuery());

            User u=User.getById(Long.valueOf(update.getInlineQuery().getFrom().getId()));

            if(u==null){
                u=new User();
                u.setUserId(Long.valueOf(update.getInlineQuery().getFrom().getId()));
                u.setUsername(update.getInlineQuery().getFrom().getUserName());
            }

            AnswerInlineQuery answerInlineQuery = new AnswerInlineQuery();
            answerInlineQuery.setInlineQueryId(update.getInlineQuery().getId());

            List<InlineQueryResult> inlineQueryResults = new ArrayList<>();
            answerInlineQuery.setResults(inlineQueryResults);

            if(update.getInlineQuery().getQuery().equals(INLINE_IMG_TOCKEN) && u.getFileIdForInline()!=null){

               // String uuid=UUID.fromString(u.getFileIdForInline()).toString();

                /*send an aleady encode QR*/
                InlineQueryResultCachedPhoto inlineQueryResultCachedPhoto=new InlineQueryResultCachedPhoto().setPhotoFileId(u.getFileIdForInline());

                inlineQueryResultCachedPhoto.setId(u.getFileIdForInline());
                inlineQueryResults.add(inlineQueryResultCachedPhoto);

                logger.info("Load cached photo------------------------------");


            }
            else {
                /*save data in order to encode a new QR*/
                u.setTextToEncode(update.getInlineQuery().getQuery());

                User.saveUser(u);

                answerInlineQuery.setSwitchPmParameter("encode");
                answerInlineQuery.setSwitchPmText("Encode: " + update.getInlineQuery().getQuery());


            }

            try {
                answerInlineQuery.setCacheTime(0);
                logger.info("send result= " + qrCodeBot.answerInlineQuery(answerInlineQuery));
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        else if(update.hasMessage() && update.getMessage().getContact()!=null){

            /* Vcart example

            BEGIN:VCARD
            VERSION:3.0
            FN:Paolo Rossi
            ADR:;;123 Street;City;Region;PostalCode;Country
            TEL:+908888888888
            TEL:+901111111111
            TEL:+902222222222
            EMAIL;TYPE=home:homeemail@example.com
            EMAIL;TYPE=work:workemail@example.com
            URL:http://www.google.com
            END:VCARD
             */

            updateChatAndUserInformation();

            Contact contact=update.getMessage().getContact();

            String text="BEGIN:VCARD\n" +
                    "VERSION:3.0\n" +
                    "FN:"+(contact.getFirstName()!=null?contact.getFirstName():"")+" "+
                    (contact.getLastName()!=null?contact.getLastName():"")+"\n" +
                    "TEL:"+(contact.getPhoneNumber()!=null?contact.getPhoneNumber():"")+"\n" +
                    "END:VCARD";


            InputStream in=getQRInputStream(text);

            if(in==null){
                sendErrorMessage("Unable to encode");
                return;
            }

            SendPhoto sendPhoto=new SendPhoto();
            sendPhoto.setChatId(update.getMessage().getChatId());

            sendPhoto.setNewPhoto("qrcode_contact",in);

            try {
                qrCodeBot.sendPhoto(sendPhoto);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                sendErrorMessage("Unable to encode");
                return;
            }
        }
        else if(update.hasMessage() && update.getMessage().hasLocation()){

            updateChatAndUserInformation();

            Location loc=update.getMessage().getLocation();

            String text="geo:"+loc.getLatitude()+","+loc.getLongitude();

            InputStream is=getQRInputStream(text);

            if(is==null){
                sendErrorMessage("Unable to encode the geo informations");
                return;
            }

            SendPhoto sendPhoto=new SendPhoto();
            sendPhoto.setChatId(update.getMessage().getChatId());

            sendPhoto.setNewPhoto("QRcode_geo",is);

            try {
                qrCodeBot.sendPhoto(sendPhoto);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                sendErrorMessage("Unable to send the photo");
                return;
            }
        }
        else if(update.hasMessage() && update.getMessage().hasDocument()){

            updateChatAndUserInformation();

            Document document=update.getMessage().getDocument();
            logger.info("Document from: "+update.getMessage().getFrom().getUserName()+" name: "+document.getFileName()
            + "mime: "+document.getMimeType()+"size: "+document.getFileSize());

            if(document.getFileSize()>MAX_FILE_SIZE){
                sendErrorMessage("File to big ( max "+MAX_FILE_SIZE +" bytes)");
                return;
            }

            GetFile getFile=new GetFile();
            getFile.setFileId(document.getFileId());

            File file;
            try {
                file= qrCodeBot.getFile(getFile);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                logger.error("File "+document.getFileName()+" not found");
                sendErrorMessage("Unable to download the file");
                return;
            }

            InputStream is;
            try {
                is=getFileInputStream(file);
            } catch (IOException e) {
                e.printStackTrace();
                logger.error("Unable to get inputstream "+document.getFileName());
                sendErrorMessage("Unable to download the file");
                return;
            }


            BufferedReader reader=new BufferedReader(new InputStreamReader(is));

            String text="";
            char c[]=new char[1];
            try {
                int i=0;
                while (reader.read(c)>-1 && i<MAX_FILE_SIZE){
                    text+=c[0];
                    i++;
                }

                if(i>=MAX_FILE_SIZE){
                    sendErrorMessage("Unable to read the file");
                    logger.error("Unable to read the file");
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage("Unable to read the file");
                logger.error("Unable to read the file"+document.getFileName());
                return;
            }


            SendPhoto sendPhoto=new SendPhoto();
            sendPhoto.setChatId(update.getMessage().getChatId());

            InputStream qrInputStream=getQRInputStream(text);

            sendPhoto.setNewPhoto("QRCode",qrInputStream);

            try {
                qrCodeBot.sendPhoto(sendPhoto);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                sendErrorMessage("Unable to send the QRCode");
                return;
            }


        }

    }

    private boolean decodeAndSendLocation(String text) {
        String temp=text.toLowerCase().replace("geo:","");
        String coordSplit[]=temp.split(",");

        SendLocation sendLocation=new SendLocation();
        sendLocation.setChatId(update.getMessage().getChatId());

        try {
            if(coordSplit.length==4){
                sendLocation.setLatitude(Float.parseFloat(coordSplit[0]+","+coordSplit[1]));
                sendLocation.setLongitude(Float.parseFloat(coordSplit[2]+","+coordSplit[3]));
            }
            else if(coordSplit.length==2){
                sendLocation.setLatitude(Float.parseFloat(coordSplit[0]));
                sendLocation.setLongitude(Float.parseFloat(coordSplit[1]));
            }
            else {
                sendErrorMessage(text);
                logger.error("Unable to generate the location for: "+text);
                return true;
            }
        }
        catch (Exception e){
            sendErrorMessage(text);
            logger.error("Unable to generate the location for: "+text);
            return true;
        }

        try {
            qrCodeBot.sendLocation(sendLocation);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendErrorMessage("Unable to send the location");
            return true;
        }
        return false;
    }

    private boolean decodeAndSendContact(SendMessage message, String text) {
        if(logger.isInfoEnabled()){
            logger.info("decoding contact");
        }

        SendContact sendContact=new SendContact();
        sendContact.setChatId(update.getMessage().getChatId());

                    /*retrieve first and last name*/
        int ind=text.indexOf("FN:")+3;

        if(ind!=-1) {
            String temp = text.substring(ind, text.indexOf('\n', ind));

            String lastAndFirstName[] = temp.split(" ");

            sendContact.setFirstName(lastAndFirstName[0]);
            if(lastAndFirstName.length>1){
                sendContact.setLastName(lastAndFirstName[1]);
            }
        }

                    /*if vcard is enabled, send also vcard file*/
        if(VCARD_ENABLED)
            sendVcardFile(text,sendContact.getFirstName());

        ind=text.indexOf("TEL:")+4;

        if(ind!=-1){
            sendContact.setPhoneNumber(text.substring(ind,text.indexOf('\n',ind)));
        }

        try {
            qrCodeBot.sendContact(sendContact);

            return true;
        } catch (TelegramApiException e) {
            e.printStackTrace();
            message.setText(text);
        }
        return false;
    }

    private void sendVcardFile(String text,String name) {
        SendDocument sendDocument=new SendDocument();
        sendDocument.setChatId(update.getMessage().getChatId());


        InputStream is=new ByteArrayInputStream(text.getBytes());

        if(is==null)
            return;

        String documetName="vCard";

        if(name!=null)
            documetName=name;

        sendDocument.setNewDocument(documetName+".vcard",is);

        try {
            qrCodeBot.sendDocument(sendDocument);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    }

    private void sendHelpMessage() {
        String helpText="- Write /encode <text>: the bot will encode the <text> inside a QRCode!\n"+
                "- Write /encode_wifi <SSID> <WPA|WEP> <password>: the bot will encode the wifi credentials!\n" +
                "- Write /encode_wifi <SSID> <password> for WPA network!\n";

        String text2="- Click :paperclip: and send a *photo* with a QRCode: the bot will decode it!\n" +
                "- Send a *Location* or a *Contact*: the bot will encode it in a QRCode!\n" +
                "- Use the bot also in chats: write @"+qrCodeBot.getBotUsername().replace("_","\\_")+" <text> to send " +
                "your friends a QRCoded message! (inline mode)";

        String text3="- Write /help or /instruction to see the commands again! :yum:";

        SendMessage message=new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        //message.enableMarkdown(true);
        message.setText(EmojiParser.parseToUnicode(helpText));



        SendMessage message1=new SendMessage();
        message1.setChatId(update.getMessage().getChatId());
        message1.enableMarkdown(true);
        message1.setText(EmojiParser.parseToUnicode(text2));

        SendMessage message2=new SendMessage();
        message2.setChatId(update.getMessage().getChatId());
        message2.enableMarkdown(true);
        message2.setText(EmojiParser.parseToUnicode(text3));

        qrCodeBot.sendResponse(message1);
        qrCodeBot.sendResponse(message);
        qrCodeBot.sendResponse(message2);

        SendMessage enjoyMessage=new SendMessage();
        enjoyMessage.setChatId(update.getMessage().getChatId());
        enjoyMessage.setText(EmojiParser.parseToUnicode("Enjoy! :grin:"));

        qrCodeBot.sendResponse(enjoyMessage);

        /*SendPhoto sendPhoto=new SendPhoto();
        sendPhoto.setChatId(update.getMessage().getChatId());

        sendPhoto.setNewPhoto("sendQr",getClass().getClassLoader().getResourceAsStream("img/sendQr.png"));

        try {
            qrCodeBot.sendPhoto(sendPhoto);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            logger.error("Unable to send the photo");
        }*/
    }

    private InputStream getQRInputStream(String text) {
        BufferedImage bufferedImage;
        try {
            bufferedImage=createQr(text);
        } catch (WriterException e) {
            sendErrorMessage("Unable to encode the image");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            sendErrorMessage("Unable to encode the image");
            e.printStackTrace();
            return null;
        }

                    /*get a InputStream with the qrcode*/
        InputStream is = getInputStreamFromBufferedImage(bufferedImage);
        return is;
    }

    private InputStream getInputStreamFromBufferedImage(BufferedImage bufferedImage) {
        ByteArrayOutputStream baos=new ByteArrayOutputStream();

        try {
            ImageIO.write(bufferedImage,"jpg",baos);
            baos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }

    private void updateChatAndUserInformation(){
        updateChatAndUserInformation(update.getMessage().getChatId(),Long.valueOf(update.getMessage().getFrom().getId()),
                update.getMessage().getFrom().getUserName());
    }

    private void updateChatAndUserInformation(Long chatId,Long userId,String username) {

        if(!qrCodeBot.dbLoggingEnabled())
            return;

        ChatEntity c=ChatEntity.getById(chatId);

        if(c==null){
            c=new ChatEntity();
            c.setChatId(chatId);
        }

        c.setLasUse(new Date(System.currentTimeMillis()));

        if(c.getNumberOfUses()==null){
            c.setNumberOfUses(0L);
        }

        if(c.getNumberOfUses()<MAX_USE_NUM)
            c.setNumberOfUses(c.getNumberOfUses()+1);

        User u=User.getById(userId);

        if(u==null){
            u=new User();
            u.setUserId(userId);
        }

        u.setUsername(username);

        u=User.saveUser(u);

        if(c.getUsers()==null){
            c.setUsers(new HashSet<>());
        }

        c.getUsers().add(u);

        u=User.saveUser(u);
        c=ChatEntity.saveChat(c);
    }

    private Map getHintMap() {
        Map hintMap = new HashMap();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        return hintMap;
    }

    private BufferedImage createQr(String text) throws WriterException, IOException{
        //String charset, Map hintMap, int qrCodeheight, int qrCodewidth)
        Map hintMap = getHintMap();

        /* used for creating bigger images */
        int mult=1;

        if(text.length()>SIZE_THRESHOLD)
            mult+=text.length()/SIZE_THRESHOLD;

        BitMatrix matrix = new MultiFormatWriter().encode(
                new String(text.getBytes(CHARSET), CHARSET),
                BarcodeFormat.QR_CODE, WIDTH_QR*mult, HEIGHT_QR*mult, hintMap);

        logger.info("Moltiplicator= "+mult);

        BufferedImage bufferedImage=MatrixToImageWriter.toBufferedImage(matrix);

        return bufferedImage;
    }

    private BufferedImage createDataMatrix(String text) throws WriterException {
        Map hintMap =new HashMap();
        hintMap.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);

        BitMatrix matrix= new MultiFormatWriter().encode(text,
                BarcodeFormat.DATA_MATRIX,WIDTH_DM,HEIGHT_DM);


        DataMatrixWriter writer = new DataMatrixWriter();

        matrix=writer.encode(text,BarcodeFormat.DATA_MATRIX,matrix.getWidth(),matrix.getHeight(),hintMap);


        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private InputStream getFileInputStream(File file) throws IOException {
        URL url=new URL(file.getFileUrl(qrCodeBot.getBotToken()));
        InputStream is=url.openStream();

        //Files.copy(is,Paths.get("download.jpg"));
        return is;
    }

    public static String readQRCode(InputStream is, Map hintMap) throws FileNotFoundException, IOException, NotFoundException {
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(
                        ImageIO.read(is))));
        Result qrCodeResult = new MultiFormatReader().decode(binaryBitmap,
                hintMap);
        return qrCodeResult.getText();
    }

    private void sendErrorMessage(String text) {
        SendMessage message=new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText(text);
        qrCodeBot.sendResponse(message);
    }
}
