package tavonatti.stefano.bots.qrcodebot;

import org.apache.log4j.Logger;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tavonatti.stefano.bots.qrcodebot.tasks.UpdateTask;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class QrCodeBot extends TelegramLongPollingBot{

    public static Logger logger=Logger.getLogger(QrCodeBot.class);

    private final static String PROPERTIES_FILE_NAME="bot_config.properties";

    private String username;
    private String token;
    private boolean dbLogging=false;
    private ThreadPoolExecutor executor;

    public QrCodeBot(){
        super();
        loadConfiguration();

        initializeExecutor();

        if(dbLoggingEnabled()){
            logger.info("DB logging enabled");
        }
        else {
            logger.info("DB logging disabled");
        }
    }

    private void initializeExecutor() {
        if(logger.isInfoEnabled()){
            logger.info("Initializing thread pool...");
        }

        executor= (ThreadPoolExecutor) Executors.newCachedThreadPool();
    }

    public void onUpdateReceived(Update update) {
        executor.execute(new UpdateTask(this,update));

        if(logger.isInfoEnabled()){
            logger.info("ThreadPool size: "+executor.getPoolSize());
        }
    }

    public String getBotUsername() {
        return username;
    }

    public String getBotToken() {
        return token;
    }

    public void sendResponse(SendMessage message){//TODO check 4096 chars
        try {
            sendMessage(message);
            logger.info(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void loadConfiguration(){
        if(logger.isInfoEnabled()) {
            logger.info("Loading configuration... ");
        }

        Properties prop = new Properties();
        InputStream input = null;

        try {

            input = new FileInputStream(PROPERTIES_FILE_NAME);

            // load a properties file
            prop.load(input);

            // get the property value and print it out
            username=prop.getProperty("username");
            token=prop.getProperty("token");
            dbLogging=Boolean.parseBoolean(prop.getProperty("db-logging","false"));

        } catch (IOException ex) {
            ex.printStackTrace();
            logger.error("fill the bot_config.properties file");

            // create empty properties file
            createPropertiesFile();

            System.exit(1);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void createPropertiesFile(){
        Properties prop=new Properties();

        prop.setProperty("username","<username>");
        prop.setProperty("token","<token>");
        prop.setProperty("db-logging","false");

        try {
            OutputStream outputStream=new FileOutputStream(PROPERTIES_FILE_NAME);
            prop.store(outputStream,"Bot credential");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            logger.error("unable to create file");
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("unable to create file");
        }
    }

    public boolean dbLoggingEnabled(){
        return dbLogging;
    }
}
