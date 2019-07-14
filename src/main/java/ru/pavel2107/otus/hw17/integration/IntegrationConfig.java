package ru.pavel2107.otus.hw17.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoAuthor;
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoBook;
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoGenre;

@Configuration
@IntegrationComponentScan
public class IntegrationConfig {

    private final Logger logger = LoggerFactory.getLogger("Integration");

    @Autowired private MongoTemplate mongoTemplate;

    @MessagingGateway( name = "genreInterface")
    public interface genreInterface{
        @Gateway( requestChannel = "genreChannelIn")
        void process(MongoGenre mongoGenre);
    }

    //
    // переносим только жанр фантастики
    //
    @Bean
    public IntegrationFlow genreFlow(){
        return IntegrationFlows.from( "genreChannelIn")
                .filter( MongoGenre.class, mongoGenre -> {
                    boolean result = mongoGenre.getId().equals("2");
                    if( result){
                        logger.info( "Жанр " + mongoGenre.getName() + " переносим");
                    }
                    else {
                        logger.info( "Жанр " + mongoGenre.getName() + " НЕ переносим");
                    }
                    return result;
                }
                )
                .handle( message -> {
                    MongoGenre mongoGenre = (MongoGenre )message.getPayload();
                    logger.info( "Записываем genre.id=" + mongoGenre.getId());
                    mongoTemplate.save( mongoGenre);
                    logger.info( "Записали genre.id=" + mongoGenre.getId());
                })
                .get();
    }

    @MessagingGateway( name = "authorInterface")
    public interface authorInterface{
        @Gateway( requestChannel = "authorChannelIn")
        void process(MongoAuthor mongoAuthor);
    }

    //
    // переносим всех авторов
    //
    @Bean
    public IntegrationFlow authorFlow(){
        return IntegrationFlows.from( "authorChannelIn")
                .handle( message -> {
                    MongoAuthor mongoAuthor = (MongoAuthor)message.getPayload();
                    logger.info( "Записываем author.id=" + mongoAuthor.getId());
                    mongoTemplate.save( mongoAuthor);
                    logger.info( "Записали author.id=" + mongoAuthor.getId());
                })
                .get();
    }

    @MessagingGateway( name = "bookInterface")
    public interface bookInterface{
        @Gateway( requestChannel = "bookChannelIn")
        void process(MongoBook mongoBook);
    }

    //
    // переносим только жанр фантастики
    //
    @Bean
    public IntegrationFlow bookFlow(){
        return IntegrationFlows.from( "bookChannelIn")
                .filter( MongoBook.class, mongoBook ->{
                    boolean result =  mongoBook.getGenre().getId().equals("2");
                    if( result){
                        logger.info( "Книгу " + mongoBook.getName() + " переносим");
                    }
                    else {
                        logger.info( "Книгу " + mongoBook.getName() + " НЕ переносим");
                    }
                    return result;
                } )
                .handle( message -> {
                    System.out.println( message);
                    MongoBook mongoBook = (MongoBook)message.getPayload();
                    logger.info( "Записываем genre.id=" + mongoBook.getId());
                    mongoTemplate.save( mongoBook);
                })
                .get();
    }
}
