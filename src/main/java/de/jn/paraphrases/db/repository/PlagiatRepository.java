package de.jn.paraphrases.db.repository;

import de.jn.paraphrases.db.entity.Plagiat;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by jan on 28.08.17.
 */
public interface PlagiatRepository extends CrudRepository<Plagiat, Integer> {

    public Plagiat findOneByUrl(String url);
}
