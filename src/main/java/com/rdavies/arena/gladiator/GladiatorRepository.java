package com.rdavies.arena.gladiator;

import com.rdavies.arena.gladiator.records.Gladiator;
import com.rdavies.arena.gladiator.records.Stats;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Package-private, so the hidden stat columns are read only through GladiatorService. */
interface GladiatorRepository extends CrudRepository<Gladiator, Long> {

    List<Gladiator> findAllByOrderByIdAsc();

    /** save() can't write the stat columns, which Gladiator leaves out, so a roster loads through here. */
    @Modifying
    @Query("""
            INSERT INTO gladiators (slug, name, epithet, bio, hp, attack_min, attack_max, defence, agility)
            VALUES (:slug, :name, :epithet, :bio, :hp, :attackMin, :attackMax, :defence, :agility)""")
    void insert(@Param("slug") String slug, @Param("name") String name, @Param("epithet") String epithet,
                @Param("bio") String bio, @Param("hp") int hp, @Param("attackMin") int attackMin,
                @Param("attackMax") int attackMax, @Param("defence") int defence, @Param("agility") int agility);

    @Modifying
    @Query("UPDATE gladiators SET elo = elo + :delta WHERE id = :id")
    void addElo(@Param("id") long id, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE gladiators SET elo = :elo WHERE id = :id")
    void setElo(@Param("id") long id, @Param("elo") int elo);

    @Query(value = "SELECT hp, attack_min, attack_max, defence, agility FROM gladiators WHERE id = :id",
            rowMapperClass = StatsRowMapper.class)
    Optional<Stats> findStats(@Param("id") long id);

    class StatsRowMapper implements RowMapper<Stats> {
        @Override
        public Stats mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Stats(rs.getInt("hp"), rs.getInt("attack_min"), rs.getInt("attack_max"),
                    rs.getInt("defence"), rs.getInt("agility"));
        }
    }
}
