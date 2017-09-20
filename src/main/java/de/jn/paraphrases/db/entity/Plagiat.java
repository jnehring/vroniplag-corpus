package de.jn.paraphrases.db.entity;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;

/**
 * Created by jan on 28.08.17.
 */
@Entity
@Getter
@Setter
@ToString
public class Plagiat {

    @Id
    String url;

    @Lob
    String fullHtml;

    @Lob
    String sourceText;

    @Lob
    String rawSourceText;

    @Lob
    String plagiatText;

    @Lob
    String rawPlagiatText;

    String source;

    String type;

    boolean gesichtet;

    String fragmentIdentifier;

    String sichtunsergebnis;

    public enum Type{
        BauernOpfer‎,
        KomplettPlagiat‎,
        ShakeAndPaste‎,
        Verschleierung‎,
        VerschärftesBauernopfer‎
    }

    public enum Source{
        Vroniplag,
        Gutenplag
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
