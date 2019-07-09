package ru.pavel2107.otus.hw17.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;
import java.util.List;

import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;




import ru.pavel2107.otus.hw17.mongoDB.domain.MongoGenre;
import ru.pavel2107.otus.hw17.rdbms.domain.Genre;


@Configuration
@EnableBatchProcessing
public class GenreStepConfig {
    private final Logger logger = LoggerFactory.getLogger("Batch");

    final private String SELECT_SQL = "select * from genre";

    @Autowired private JobBuilderFactory jobBuilderFactory;
    @Autowired private StepBuilderFactory stepBuilderFactory;
    @Autowired private DataSource dataSource;
    @Autowired private MongoTemplate mongoTemplate;

    @Autowired private ApplicationContext context;


    @Bean
    JdbcCursorItemReader<Genre> readerGenre(){
        JdbcCursorItemReader<Genre> reader = new JdbcCursorItemReader<>();
        reader.setDataSource( dataSource);
        reader.setSql( SELECT_SQL);
        reader.setRowMapper((resultSet, i) -> {
            Genre genre = new Genre();
            genre.setName( resultSet.getString( "name"));
            genre.setId(   resultSet.getLong( "id"));
            return genre;
        });
        return reader;
    }

   @Bean
   ItemWriter writerGenre(){
       return (ItemWriter<MongoGenre>) list -> {
           DirectChannel genreChannel = context.getBean( "genreChannel", DirectChannel.class);
           for( int i = 0; i < list.size(); i++){
               MongoGenre mongoGenre = list.get( i);
               Message<MongoGenre> genreMessage = MessageBuilder
                       .withPayload(mongoGenre)
                       .setHeader("command", "save")
                       .build();
               genreChannel.send(genreMessage);
               logger.info( "Отправляем genre.id=" + mongoGenre.getId());
           }
       };
   }

   @Bean
    ItemProcessor processorGenre(){
        return ( ItemProcessor<Genre, MongoGenre>) genre ->{
            MongoGenre mongoGenre = new MongoGenre();
            mongoGenre.setId(   genre.getId().toString());
            mongoGenre.setName( genre.getName());
            return mongoGenre;
        };
    }


    //
    // переносим только жанр фантастики
    //
    @Bean
    public IntegrationFlow genreFlow(){
        return IntegrationFlows.from( "genreChannel")
                .filter( MongoGenre.class, mongoGenre -> mongoGenre.getId().equals("1") )
                .handle( message -> {
                    System.out.println( message);
                    MongoGenre mongoGenre = (MongoGenre )message.getPayload();
                    logger.info( "Записываем genre.id=" + mongoGenre.getId());
                    mongoTemplate.save( mongoGenre);
                })
                .get();
    }

    @Bean
    public Step stepGenre( JdbcCursorItemReader<Genre> readerGenre, ItemWriter writerGenre, ItemProcessor processorGenre){
        TaskletStep stepGenre = stepBuilderFactory.get("stepGenre")
                .chunk(5)
                .reader(readerGenre)
                .writer( writerGenre)
                .processor(processorGenre)
                .listener(new ItemReadListener() {
                    public void beforeRead() { logger.info("Начало чтения"); }
                    public void afterRead(Object o) { logger.info("Конец чтения"); }
                    public void onReadError(Exception e) { logger.info("Ошибка чтения"); }
                })
                .listener(new ItemWriteListener() {
                    public void beforeWrite(List list) { logger.info("Начало записи"); }
                    public void afterWrite(List list) { logger.info("Конец записи"); }
                    public void onWriteError(Exception e, List list) { logger.info("Ошибка записи"); }
                })
                .listener(new ItemProcessListener() {
                    public void beforeProcess(Object o) {logger.info("Начало обработки");}
                    public void afterProcess(Object o, Object o2) {logger.info("Конец обработки");}
                    public void onProcessError(Object o, Exception e) {logger.info("Ошбка обработки");}
                })
                .listener(new ChunkListener() {
                    public void beforeChunk(ChunkContext chunkContext) {logger.info("Начало пачки");}
                    public void afterChunk(ChunkContext chunkContext) {logger.info("Конец пачки");}
                    public void afterChunkError(ChunkContext chunkContext) {logger.info("Ошибка пачки");}
                })

                .build();
        return stepGenre;
    }
}
