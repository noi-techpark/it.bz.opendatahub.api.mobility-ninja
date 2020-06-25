package it.bz.idm.bdp.ninja.utils.jsonserializer;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.Decoder;
import com.jsoniter.spi.Encoder;
import com.jsoniter.spi.JsonException;
import com.jsoniter.spi.JsoniterSpi;

public class JsonIterSqlTimestampSupport {

    private static String pattern;
    private final static ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(pattern);
        }
    };

    public static synchronized void enable(String pattern) {
        if (JsonIterSqlTimestampSupport.pattern != null) {
            throw new JsonException("SqlTimestampSupport.enable can only be called once");
        }
        JsonIterSqlTimestampSupport.pattern = pattern;
        JsoniterSpi.registerTypeEncoder(Timestamp.class, new Encoder.ReflectionEncoder() {
            @Override
            public void encode(Object obj, JsonStream stream) throws IOException {
                stream.writeVal(sdf.get().format(obj));
            }

            @Override
            public Any wrap(Object obj) {
                return Any.wrap(sdf.get().format(obj));
            }
        });
        JsoniterSpi.registerTypeDecoder(Timestamp.class, new Decoder() {
            @Override
            public Object decode(JsonIterator iter) throws IOException {
                try {
                    return sdf.get().parse(iter.readString());
                } catch (ParseException e) {
                    throw new JsonException(e);
                }
            }
        });
    }
}
