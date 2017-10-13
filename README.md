# QRCodeBot

A Telegram bot for encoding and decoding QRcode.

This bot can:

- Encode/Decode a text from/to a QR code;
- Encode/Decode a vCard from/to a QRCode;
- Encode/Decode a position (geo) from/to a QRCode;
- Encode a small text file in a QRCode.

Try the original bot! Write to [http://t.me/qr_reader_bot](http://t.me/qr_reader_bot "")
 

# Build

Before building the project set the following environment variables, with your databse connection settings:

```
export QRCODEBOT_USERNAME="<bot username>"
export QRCODEBOT_TOCKEN="<bot tocken>"
export DB_USERNAME_QRCODEBOT="<DB username>"
export DB_PASSWORD_QRCODEBOT="<DB password>"
export DB_URL_QRCODEBOT="jdbc:postgresql://<ip>:<port>/<db name>"

```

Next build jar:

```
mvn package
```

In order to run the Bot type:

```
java -jar target/qrcode_bot-1.0-SNAPSHOT-jar-with-dependencies.jar
```