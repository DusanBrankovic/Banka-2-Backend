package rs.edu.raf.si.bank2.client.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rs.edu.raf.si.bank2.client.services.interfaces.MailingServiceInterface;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

@Service
public class MailingService implements MailingServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(MailingService.class);
    private static final String from = "banka2backend@gmail.com";
    private static final String password = "idxegskltunedxog";

    @Autowired
    public MailingService() {}

    private void sendMail(String recipient, String subject, String content) throws MessagingException {
        // Setting up STMP server
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

        Session session = Session.getDefaultInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        email.setSubject(subject);
        email.setText(content);

        Transport.send(email);

        // TODO: da li ovde treba nesto da se zatvori? npr session ili
        //  transport?
    }




}
