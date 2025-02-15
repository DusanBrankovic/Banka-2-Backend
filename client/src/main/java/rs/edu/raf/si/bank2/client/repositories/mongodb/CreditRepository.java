package rs.edu.raf.si.bank2.client.repositories.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import rs.edu.raf.si.bank2.client.models.mongodb.Client;
import rs.edu.raf.si.bank2.client.models.mongodb.Credit;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditRepository extends MongoRepository<Credit, String> {

    List<Credit> findAllByClientEmail(String clientEmail);
}
