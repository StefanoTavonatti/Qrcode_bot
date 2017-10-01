package tavonatti.stefano.bots.qrcodebot.tasks;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.log4j.Logger;
import org.jfree.graphics2d.svg.SVGGraphics2D;
import org.jfree.graphics2d.svg.SVGGraphicsDevice;
import org.jfree.graphics2d.svg.SVGUtils;
import org.telegram.telegrambots.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.SendDocument;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.File;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.PhotoSize;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputMessageContent;
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultPhoto;
import org.telegram.telegrambots.api.objects.inlinequery.result.chached.InlineQueryResultCachedPhoto;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tavonatti.stefano.bots.qrcodebot.QrCodeBot;
import tavonatti.stefano.bots.qrcodebot.entities.ChatEntity;
import tavonatti.stefano.bots.qrcodebot.entities.User;
import tavonatti.stefano.bots.qrcodebot.entities.extra.Role;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class UpdateTask implements Runnable {

    public static Logger logger=Logger.getLogger(UpdateTask.class);

    private final static String CHARSET="UTF-8"; // or "ISO-8859-1"

    private final static int WIDTH_QR=500;
    private final static int HEIGHT_QR=500;
    private final static int SIZE_THRESHOLD=250;

    private final static long MAX_USE_NUM=9223372036854775000L;
    private final static String INLINE_IMG_TOCKEN="###";

    private final static int WIDTH_DM=64;
    private final static int HEIGHT_DM=64;

    private QrCodeBot qrCodeBot;
    private Update update;

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
                            getHelpMessage(message);
                            qrCodeBot.sendResponse(message);
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
                case "/help":
                    getHelpMessage(message);
                    qrCodeBot.sendResponse(message);
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

                case "/encode_svg":
                    if(splits.length<2){
                        message.setText("use:\n/encode <text>");
                        qrCodeBot.sendResponse(message);
                        break;
                    }

                    String textSvg="";

                    //i=1 in order to skyp the encode command
                    for(int i=1;i<splits.length;i++){
                        if(i!=1){
                            textSvg+=" ";
                        }
                        textSvg+=splits[i];
                    }

                    SendDocument sendDocument=new SendDocument();
                    sendDocument.setChatId(update.getMessage().getChatId());

                    try {
                        sendDocument.setNewDocument("qrcode.svg",createQrSVG(textSvg));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        sendErrorMessage("Unable to encode the text");
                        return;
                    } catch (WriterException e) {
                        e.printStackTrace();
                    }

                    try {
                        qrCodeBot.sendDocument(sendDocument);
                        System.out.println("inviato");
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

                    message.setText(chatsString.replace("_","\\_"));
                    message.enableMarkdown(true);

                    qrCodeBot.sendResponse(message);

                    return;

                default:
                    message.setText("Type /help for the list of available commands");
                    qrCodeBot.sendResponse(message);
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
                message.setText(readQRCode(is,hintMap));
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

    }

    private void getHelpMessage(SendMessage message) {
        String helpText="/encode <text>: encode the <text> inside a QRCode\n"+
                "send a photo with a QrCode in order to decode it.";

        message.setText(helpText);
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
        BitMatrix matrix = getQRCodeBitMatrix(text);


        BufferedImage bufferedImage=MatrixToImageWriter.toBufferedImage(matrix);

        return bufferedImage;
    }

    private InputStream createQrSVG(String text) throws UnsupportedEncodingException, WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode("http://www.jfree.org/jfreesvg",
                BarcodeFormat.QR_CODE,160,160);

        //BitMatrix bitMatrix=getQRCodeBitMatrix(text);

        //source: https://stackoverflow.com/questions/10789059/create-qr-code-in-vector-image

        int w = bitMatrix.getWidth();
        SVGGraphics2D g2 = new SVGGraphics2D(w, w);
        g2.setColor(Color.BLACK);

        /*for(int y=0; y<bitMatrix.getWidth();y++){
            BitArray bitArray= bitMatrix.getRow(y,null);
            for (int x = 0; x < bitArray.getSize(); x++) {
                if (bitArray.get(x)) {
                    g2.fillRect(x,y,bitMatrix.getRowSize(),bitMatrix.getRowSize());
                }
                System.out.println("qui "+x+" "+y);
            }
        }*/
        System.out.println("w= "+w);
        for (int xIndex = 0; xIndex < w; xIndex = xIndex +  1) {

            for (int yIndex = 0; yIndex < bitMatrix.getHeight(); yIndex = yIndex +  1) {
                if (bitMatrix.get(xIndex,yIndex)) {
                    g2.fillRect(xIndex, yIndex, 1, 1);

                }
                System.out.println("qui "+xIndex+" "+yIndex);
            }
        }
        System.out.println("qui finr");
        //String result="<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n";
        //result+=g2.getS
        byte[] bytes=g2.getSVGDocument().getBytes();

        try {
            SVGUtils.writeToSVG(new java.io.File("Pri.svg"),g2.getSVGElement());
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("byte_size "+bytes.length);

        return new ByteArrayInputStream(bytes);
    }

    private BitMatrix getQRCodeBitMatrix(String text) throws WriterException, UnsupportedEncodingException {
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
        return matrix;
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
