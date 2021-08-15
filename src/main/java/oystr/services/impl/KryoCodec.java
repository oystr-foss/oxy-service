package oystr.services.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import oystr.models.Peer;
import oystr.models.PeerState;
import oystr.services.Codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;


public class KryoCodec implements Codec<Peer> {
    private final Kryo kryo = new Kryo();

    public KryoCodec() {
        kryo.register(LocalDateTime.class);
        kryo.register(PeerState.class);
        kryo.register(Peer.class);
    }

    @Override
    public String decodeKey(ByteBuffer buffer) {
        return new String(toByteArray(buffer), StandardCharsets.UTF_8);
    }

    @Override
    public Peer decodeValue(ByteBuffer buffer) {
        byte[] bytes = toByteArray(buffer);
        Input input = new Input(bytes);
        Peer object = kryo.readObject(input, Peer.class);
        System.out.println(object.getHost());
        input.close();

        return object;
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        return ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
    }

    public static int length(Object obj) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(out);
            os.writeObject(obj);
            return out.toByteArray().length;
        } catch (Exception e) {
            return 2048;
        }
    }

    @Override
    public ByteBuffer encodeValue(Peer value) {
        Output output = new Output(256);
        kryo.writeObject(output, value);
        System.out.println(value.getHost());
        ByteBuffer buffer = ByteBuffer.wrap(output.getBuffer().clone());
        output.close();

        return buffer;
    }

    private byte[] toByteArray(ByteBuffer buffer) {
//        if (buffer.hasArray()) {
//            return buffer.array();
//        }

        buffer.flip();
        byte[] bytes = new byte[256];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
