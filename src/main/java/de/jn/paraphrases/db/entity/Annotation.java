package de.jn.paraphrases.db.entity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Annotation {
	
	@Id
	String annotationIdentifier;
	
	String url;
	String fragmentIdentifier;
	
	@Lob
	String plagiatSent;
	
	@Lob
	String srcSent;
	
   @Override
    public String toString() {
        return super.toString();
    }

}
