package tavonatti.stefano.bots.qrcodebot.tasks;

import org.apache.log4j.Logger;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import tavonatti.stefano.bots.qrcodebot.QrCodeBot;

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
    }
}
