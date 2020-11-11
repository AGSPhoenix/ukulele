package dev.arbjerg.ukulele.data

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Duration

@Service
class GuildPropertiesService(private val repo: GuildPropertiesRepository) {
    private val cache: AsyncLoadingCache<Long, GuildProperties> = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .buildAsync { id, _ ->
                repo.findById(id)
                        .defaultIfEmpty(GuildProperties(id).apply { new = true })
                        .toFuture() }

    fun get(guildId: Long) = cache[guildId].toMono()
    suspend fun getAwait(guildId: Long) = get(guildId).awaitSingle()

    fun transform(guildId: Long, func: (GuildProperties) -> Unit): Mono<GuildProperties> = cache[guildId]
            .toMono()
            .map { func(it); it }
            .flatMap { repo.save(it) }
            .map { it.apply { new = false } }
            .doOnSuccess { cache.synchronous().put(it.guildId, it) }
}

@Table("guild_properties")
data class GuildProperties(
        @Id val guildId: Long,
        var volume: Int = 100
) : Persistable<Long> {
    @Transient var new: Boolean = false
    override fun getId() = guildId
    override fun isNew() = new
}

interface GuildPropertiesRepository : ReactiveCrudRepository<GuildProperties, Long>