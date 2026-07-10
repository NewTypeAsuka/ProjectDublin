package me.newtypeasuka.projectdublin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByName(String name);

    @Query("SELECT m FROM Member m WHERE m.name = ?1")
    Optional<Member> findByNameQuery(String name);
}