package tavonatti.stefano.bots.qrcodebot;

import org.apache.log4j.Logger;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

public class Main {

    public static Logger logger=Logger.getLogger(Main.class);

    public static void main(String args[]){
        ApiContextInitializer.init();

        TelegramBotsApi botsApi=new TelegramBotsApi();

        QrCodeBot qrCodeBot=new QrCodeBot();

        try {
            botsApi.registerBot(qrCodeBot);
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
            logger.error("Unable to register bot");
        }
    }
}
