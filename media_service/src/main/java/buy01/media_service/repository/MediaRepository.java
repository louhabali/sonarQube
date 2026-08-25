package buy01.media_service.repository;

import buy01.media_service.model.Media;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MediaRepository extends MongoRepository<Media,String> {

    List<Media> findByProductId(String productId);

}