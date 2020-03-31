package com.mrostudios

import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import javax.mail.*
import javax.mail.internet.MimeMessage


val log = LogManager.getLogger()

data class MailConfiguration(val smtpServerUrl: String, val username: String, val password: String, val emailFrom: String, val emailTo: String)
data class KonzumResult(val dostava: Boolean, val preuzimanjeDan: Boolean, val preuzimanjeNoc: Boolean)

fun main(args: Array<String>) {
    if (args.size != 7) {
        print("Invalid number of args: [intervalMinute] [smtpUrl] [username] [password] [emailFrom] [emailTo] [testEmail]")
        return
    }

    val brojMinutaProvjera = args[0].toInt()
    val smtpServer = args[1]
    val username = args[2]
    val password = args[3]
    val emailFrom = args[4]
    val emailTo = args[5]

    val mailConfiguration = MailConfiguration(smtpServer, username, password, emailFrom, emailTo)

    val testEmail = args[6].toBoolean()

    log.info("Startam aplikaciju - konzum dostava / provjera svakih $brojMinutaProvjera min")
    if (testEmail) {
        log.info("Saljem test mail")
        sendMail("Test email", mailConfiguration)
    }
    while (true) {
        try {
            log.info("Saljem request")
            val result = getKonzumResult()
            if (result.dostava || result.preuzimanjeDan || result.preuzimanjeNoc) {
                sendMail("Ima dostave ili preuzimanja!\n$result", mailConfiguration)
            }
            log.info("Dobio podatke - $result")
            Thread.sleep(brojMinutaProvjera * 60 * 1000L)
        } catch (e: Exception) {
            log.error("Greska prilikom provjere dostave", e)
        }
    }
}


private fun getKonzumResult(): KonzumResult {
    val doc = Jsoup.connect("https://www.konzum.hr/web/raspolozivi-termini").get()
    val nemaDostave = doc.select("[data-tab-type=delivery] h2").first()?.text() == "Trenutno nema dostupnih termina"
    val nemaDan = doc.select("[data-tab-type=drivein] .day-terms h2").first()?.text() == "Trenutno nema dostupnih termina"
    val nemaNoc = doc.select("[data-tab-type=drivein] .night-terms h2").first()?.text() == "Trenutno nema dostupnih termina"
    return KonzumResult(!nemaDostave, !nemaDan, !nemaNoc)
}

private fun sendMail(message: String, mailConfiguration: MailConfiguration) {
    val prop = System.getProperties()
    prop.put("mail.smtp.host", mailConfiguration.smtpServerUrl)
    prop.put("mail.smtp.auth", "true")
    prop.put("mail.smtp.port", "465")
    prop.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")

    val session = Session.getInstance(prop, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(mailConfiguration.username, mailConfiguration.password)
        }
    })
    val msg = MimeMessage(session)

    msg.setFrom(mailConfiguration.emailFrom)
    msg.setRecipients(Message.RecipientType.TO, mailConfiguration.emailTo)
    msg.setSubject("Konzum dostava!!!")
    msg.setText(message)
    Transport.send(msg)
}
