package com.example.sys.repository;

import com.example.sys.domain.SysUser;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 用户 Repository。 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM SysUser u WHERE "
            + "(:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.realName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:unitId IS NULL OR u.unitId = :unitId) "
            + "AND (:status IS NULL OR u.status = :status)")
    Page<SysUser> search(@Param("keyword") String keyword,
                         @Param("unitId") Long unitId,
                         @Param("status") Integer status,
                         Pageable pageable);
}
