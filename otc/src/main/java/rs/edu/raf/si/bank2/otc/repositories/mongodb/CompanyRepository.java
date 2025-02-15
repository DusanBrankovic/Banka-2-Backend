package rs.edu.raf.si.bank2.otc.repositories.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import rs.edu.raf.si.bank2.otc.models.mongodb.Company;

import java.util.Optional;

@Repository
public interface CompanyRepository extends MongoRepository<Company, String> {

    Optional<Company> findCompanyByName(String name);

    Optional<Company> findCompanyByRegistrationNumber(String registrationNo);

    Optional<Company> findCompanyByTaxNumber(String taxNo);

}
