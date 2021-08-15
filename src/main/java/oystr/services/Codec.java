package oystr.services;

import io.lettuce.core.codec.RedisCodec;

public interface Codec<T> extends RedisCodec<String, T> {
}
