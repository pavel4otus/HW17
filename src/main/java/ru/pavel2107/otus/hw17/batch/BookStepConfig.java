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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.Message;
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoAuthor;
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoBook;
import ru.pavel2107.otus.hw17.mongoDB.domain.MongoGenre;
import ru.pavel2107.otus.hw17.rdbms.domain.Author;
import ru.pavel2107.otus.hw17.rdbms.domain.Book;
import ru.pavel2107.otus.hw17.rdbms.domain.Comment;
import ru.pavel2107.otus.hw17.rdbms.domain.Genre;


@Configuration
@EnableBatchProcessing
public class BookStepConfig {
    private final Logger logger = LoggerFactory.getLogger("Batch");

    final private String SELECT_SQL = "select * from books";

    @Autowired private StepBuilderFactory stepBuilderFactory;
    @Autowired private DataSource dataSource;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired private ApplicationContext context;

    @Bean
    JdbcCursorItemReader<Book> readerBook(){
        JdbcCursorItemReader<Book> reader = new JdbcCursorItemReader<>();
        reader.setDataSource( dataSource);
        reader.setSql( SELECT_SQL);
        reader.setRowMapper((resultSet, i) -> {
            final String SELECT_COMMENTS_SQL = "select * from comments where book_id=:book_id";

            Book book = new Book();
            book.setName( resultSet.getString( "name"));
            book.setId(   resultSet.getLong( "id"));
            Genre g = new Genre();
            g.setId( resultSet.getLong( "genre_id"));
            book.setGenre( g);

            Author a = new Author();
            a.setId( resultSet.getLong( "author_id"));
            book.setAuthor(a);

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue( "book_id", book.getId());

            List<Comment> comments = jdbcTemplate.query(SELECT_COMMENTS_SQL, params, (resultSet1, i1) -> {
                Comment comment = new Comment();
                comment.setName( resultSet1.getString( "name"));
                comment.setComment( resultSet1.getString( "comment"));
                comment.setDateTime( resultSet1.getTimestamp( "datetime").toLocalDateTime() );

                return comment;
            });
            book.setComments( comments);

            return book;
        });

        return reader;
    }

    @Bean
    ItemWriter writerBook(){
        return (ItemWriter<MongoBook>) list -> {
            DirectChannel bookChannel = context.getBean( "bookChannel", DirectChannel.class);
            for( int i = 0; i < list.size(); i++){
                MongoBook mongoBook = list.get( i);
                Message<MongoBook> bookMessage = MessageBuilder
                        .withPayload(mongoBook)
                        .setHeader("command", "save")
                        .build();
                bookChannel.send(bookMessage);
                logger.info( "Отправляем Author.id=" + mongoBook.getId());
            }
        };
    }

    @Bean
    ItemProcessor processorBook(){
        return ( ItemProcessor<Book, MongoBook>) book ->{
            MongoBook mongoBook = new MongoBook();
            mongoBook.setId(   book.getId().toString());
            mongoBook.setName( book.getName());

            MongoAuthor a = new MongoAuthor();
            a.setId( book.getAuthor().getId().toString());
            mongoBook.setAuthor( a);

            MongoGenre g = new MongoGenre();
            g.setId( book.getAuthor().getId().toString());
            mongoBook.setGenre( g);

            return mongoBook;
        };
    }

    //
    // переносим только жанр фантастики
    //
    @Bean
    public IntegrationFlow bookFlow(){
        return IntegrationFlows.from( "bookChannel")
                .filter( MongoBook.class, mongoBook ->{
                        boolean result =  mongoBook.getGenre().getId().equals("1");
                        if( result){
                            System.out.println( "Книгу " + mongoBook.getName() + " переносим");
                        }
                        else {
                            System.out.println( "Книгу " + mongoBook.getName() + " НЕ переносим");
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


    @Bean
    public Step stepBook( JdbcCursorItemReader<Book> readerBook, ItemWriter writerBook, ItemProcessor processorBook){
        TaskletStep stepBook = stepBuilderFactory.get("stepBook")
                .chunk(5)
                .reader(readerBook)
                .writer( writerBook)
                .processor(processorBook)
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
        return stepBook;
    }
}
