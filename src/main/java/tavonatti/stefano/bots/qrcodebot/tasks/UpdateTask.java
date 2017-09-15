package tavonatti.stefano.bots.qrcodebot.tasks;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.log4j.Logger;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.SendDocument;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.File;
import org.telegram.telegrambots.api.objects.PhotoSize;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tavonatti.stefano.bots.qrcodebot.QrCodeBot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateTask implements Runnable {

    public static Logger logger=Logger.getLogger(UpdateTask.class);

    private final static String CHARSET="UTF-8"; // or "ISO-8859-1"
    private final static int WIDTH_QR=500;
    private final static int HEIGHT_QR=500;

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

            String[] splits=update.getMessage().getText().split(" ");

            String[] commandSplit=splits[0].split("@");

            if(commandSplit.length>1){
                if(!commandSplit[1].equals(qrCodeBot.getBotUsername())){
                    return;
                }
            }

            switch (commandSplit[0]){
                case "/help":
                    String helpText="/encode <text>: encode the <text> inside a QRCode\n"+
                            "send a photo with a QrCode in order to decode it.";

                    message.setText(helpText);
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
                    BufferedImage bufferedImage;
                    try {
                        bufferedImage=createQr(text);
                    } catch (WriterException e) {
                        sendErrorMessage("Unable to encode the image");
                        e.printStackTrace();
                        return;
                    } catch (IOException e) {
                        sendErrorMessage("Unable to encode the image");
                        e.printStackTrace();
                        return;
                    }

                    /*get a InputStream with the qrcode*/
                    ByteArrayOutputStream baos=new ByteArrayOutputStream();

                    try {
                        ImageIO.write(bufferedImage,"jpg",baos);
                        baos.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    InputStream is=new ByteArrayInputStream(baos.toByteArray());

                    /*send the qrcode*/
                    sendPhoto.setNewPhoto("qrcode",is);

                    try {
                        qrCodeBot.sendPhoto(sendPhoto);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }

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

    }

    private Map getHintMap() {
        Map hintMap = new HashMap();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        return hintMap;
    }

    private BufferedImage createQr(String text) throws WriterException, IOException{
        //String charset, Map hintMap, int qrCodeheight, int qrCodewidth)
        Map hintMap = getHintMap();

        BitMatrix matrix = new MultiFormatWriter().encode(
                new String(text.getBytes(CHARSET), CHARSET),
                BarcodeFormat.QR_CODE, WIDTH_QR, HEIGHT_QR, hintMap);

        BufferedImage bufferedImage=MatrixToImageWriter.toBufferedImage(matrix);

        return bufferedImage;
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
