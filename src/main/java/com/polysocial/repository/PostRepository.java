package com.polysocial.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.polysocial.entity.Posts;

public interface PostRepository extends JpaRepository<Posts, Long>{
    
   
}
