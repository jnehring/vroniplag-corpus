package de.jn.paraphrases.db.repository;

import de.jn.paraphrases.db.entity.Fragment;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by jan on 28.08.17.
 */
public interface FragmentRepository extends CrudRepository<Fragment, Integer> {

    public Fragment findOneByUrl(String url);
}
