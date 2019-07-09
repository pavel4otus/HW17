package ru.pavel2107.otus.hw17.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
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
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoAuthor;
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoGenre;
import ru.pavel2107.otus.hw17.rdbms.domain.Author;

@Configuration
@EnableBatchProcessing
public class AuthorStepConfig {
        private final Logger logger = LoggerFactory.getLogger("Batch");

        final private String SELECT_SQL = "select * from authors";

        @Autowired private StepBuilderFactory stepBuilderFactory;
        @Autowired private DataSource dataSource;
        @Autowired private MongoTemplate mongoTemplate;

        @Autowired private ApplicationContext context;

        @Bean
        JdbcCursorItemReader<Author> readerAuthor(){
            JdbcCursorItemReader<Author> reader = new JdbcCursorItemReader<>();
            reader.setDataSource( dataSource);
            reader.setSql( SELECT_SQL);
            reader.setRowMapper((resultSet, i) -> {
                Author Author = new Author();
                Author.setName( resultSet.getString( "name"));
                Author.setId(   resultSet.getLong( "id"));
                return Author;
            });

            return reader;
        }

        @Bean
        ItemWriter writerAuthor(){
            return (ItemWriter<MongoAuthor>) list -> {
                DirectChannel authorChannel = context.getBean( "authorChannel", DirectChannel.class);
                for( int i = 0; i < list.size(); i++){
                    MongoAuthor mongoAuthor = list.get( i);
                    Message<MongoAuthor> genreMessage = MessageBuilder
                            .withPayload(mongoAuthor)
                            .setHeader("command", "save")
                            .build();
                    authorChannel.send(genreMessage);
                    logger.info( "Отправляем Author.id=" + mongoAuthor.getId());
                }
            };
        }

        @Bean
        ItemProcessor processorAuthor(){
            return ( ItemProcessor<Author, MongoAuthor>) Author ->{
                MongoAuthor mongoAuthor = new MongoAuthor();
                mongoAuthor.setId(   Author.getId().toString());
                mongoAuthor.setName( Author.getName());
                return mongoAuthor;
            };
        }


    //
    // переносим всех авторов
    //
    @Bean
    public IntegrationFlow authorFlow(){
        return IntegrationFlows.from( "authorChannel")
                .handle( message -> {
                    System.out.println( message);
                    MongoAuthor mongoAuthor = (MongoAuthor)message.getPayload();
                    logger.info( "Записываем genre.id=" + mongoAuthor.getId());
                    mongoTemplate.save( mongoAuthor);
                })
                .get();
    }


        @Bean
        public Step stepAuthor( JdbcCursorItemReader<Author> readerAuthor, ItemWriter writerAuthor, ItemProcessor processorAuthor){
            TaskletStep stepAuthor = stepBuilderFactory.get("stepAuthor")
                    .chunk(5)
                    .reader(readerAuthor)
                    .writer( writerAuthor)
                    .processor(processorAuthor)
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
            return stepAuthor;
        }
}
