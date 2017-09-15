package tavonatti.stefano.bots.qrcodebot.tasks;

import org.apache.log4j.Logger;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.File;
import org.telegram.telegrambots.api.objects.PhotoSize;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tavonatti.stefano.bots.qrcodebot.QrCodeBot;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class UpdateTask implements Runnable {

    public static Logger logger=Logger.getLogger(UpdateTask.class);

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


            message.setText(update.getMessage().getText());

            qrCodeBot.sendResponse(message);
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

            try {
                downloadFile(file);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }

    }

    private void downloadFile(File file) throws IOException {
        URL url=new URL(file.getFileUrl(qrCodeBot.getBotToken()));
        InputStream is=url.openStream();

        Files.copy(is,Paths.get("doanload.jpg"));
    }

    private void sendErrorMessage(String text) {
        SendMessage message=new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText(text);
        qrCodeBot.sendResponse(message);
    }
}
