//package com.ryu.blog.service.impl;
//
//import com.ryu.blog.entity.Like;
//import com.ryu.blog.repository.CommentRepository;
//import com.ryu.blog.repository.LikeRepository;
//import com.ryu.blog.repository.PostsRepository;
//import com.ryu.blog.repository.UserRepository;
//import com.ryu.blog.service.LikeService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
///**
// * 点赞服务实现类
// */
//@Slf4j
//@Service
//public class LikeServiceImpl implements LikeService {
//
//    @Autowired
//    private LikeRepository likeRepository;
//
//    @Autowired
//    private PostsRepository postsRepository;
//
//    @Autowired
//    private CommentRepository commentRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Override
//    @Transactional
//    public Mono<Boolean> toggleLike(Integer type, Long targetId, Long userId) {
//        // 先检查点赞记录是否存在
//        return likeRepository.findByUserIdAndTypeAndTargetIdAndIsDeleted(userId, type, targetId, 0)
//                .hasElement()
//                .flatMap(exists -> {
//                    if (exists) {
//                        // 如果存在，则取消点赞
//                        return likeRepository.findByUserIdAndTypeAndTargetIdAndIsDeleted(userId, type, targetId, 0)
//                                .flatMap(like -> {
//                                    like.setIsDeleted(1);
//                                    like.setUpdateTime(LocalDateTime.now());
//                                    return likeRepository.save(like);
//                                })
//                                .flatMap(savedLike -> updateTargetLikeCount(type, targetId, false));
//                    } else {
//                        // 如果不存在，则添加点赞
//                        Like like = new Like();
//                        like.setType(type);
//                        like.setTargetId(targetId);
//                        like.setUserId(userId);
//                        like.setCreateTime(LocalDateTime.now());
//                        like.setUpdateTime(LocalDateTime.now());
//                        like.setIsDeleted(0);
//                        return likeRepository.save(like)
//                                .flatMap(savedLike -> updateTargetLikeCount(type, targetId, true));
//                    }
//                });
//    }
//
//    /**
//     * 更新目标的点赞数
//     *
//     * @param type     类型
//     * @param targetId 目标ID
//     * @param isAdd    是否增加
//     * @return 是否成功
//     */
//    private Mono<Boolean> updateTargetLikeCount(Integer type, Long targetId, boolean isAdd) {
//        if (type == 1) {
//            // 文章点赞
//            return postsRepository.findById(targetId)
//                    .flatMap(article -> {
//                        Integer views = article.getViews() == null ? 0 : article.getViews();
//                        article.setViews(views + 1);
//                        return postsRepository.save(article).map(a -> true);
//                    })
//                    .defaultIfEmpty(false);
//        } else if (type == 2) {
//            // 评论点赞
//            // 评论可能没有点赞数字段，这里假设有
//            return Mono.just(true);
//        } else {
//            return Mono.just(false);
//        }
//    }
//
//    @Override
//    public Mono<Boolean> checkLiked(Integer type, Long targetId, Long userId) {
//        return likeRepository.findByUserIdAndTypeAndTargetIdAndIsDeleted(userId, type, targetId, 0)
//                .map(like -> true)
//                .defaultIfEmpty(false);
//    }
//
//    @Override
//    public Mono<Long> getLikeCount(Integer type, Long targetId) {
//        return likeRepository.countByTypeAndTargetIdAndIsDeleted(type, targetId, 0);
//    }
//
//    @Override
//    public Flux<Like> getUserLikes(Long userId, Integer type) {
//        if (type != null) {
//            return likeRepository.findByUserIdAndTypeAndIsDeletedOrderByCreateTimeDesc(userId, type, 0)
//                    .flatMap(this::enrichLike);
//        } else {
//            return likeRepository.findByUserIdAndIsDeletedOrderByCreateTimeDesc(userId, 0)
//                    .flatMap(this::enrichLike);
//        }
//    }
//
//    /**
//     * 丰富点赞信息
//     */
//    private Mono<Like> enrichLike(Like like) {
//        if (like.getType() == 1) {
//            // 文章点赞
//            return postsRepository.findById(like.getTargetId())
//                    .doOnNext(article -> {
//                        // 这里可以设置点赞目标的信息，如文章标题等
//                    })
//                    .thenReturn(like);
//        } else if (like.getType() == 2) {
//            // 评论点赞
//            return commentRepository.findById(like.getTargetId())
//                    .doOnNext(comment -> {
//                        // 这里可以设置点赞目标的信息，如评论内容等
//                    })
//                    .thenReturn(like);
//        } else {
//            return Mono.just(like);
//        }
//    }
//
//    @Override
//    public Mono<Map<String, Object>> getUserLikesPaged(Long userId, Integer type, int page, int size) {
//        final int finalPage = page < 1 ? 1 : page;
//        final int finalSize = size < 1 ? 10 : size;
//
//        Flux<Like> likesFlux;
//        Mono<Long> countMono;
//
//        if (type != null) {
//            likesFlux = likeRepository.findByUserIdAndTypeAndIsDeletedOrderByCreateTimeDesc(userId, type, 0)
//                    .skip((finalPage - 1) * finalSize)
//                    .take(finalSize)
//                    .flatMap(this::enrichLike);
//
//            countMono = likeRepository.findByUserIdAndTypeAndIsDeletedOrderByCreateTimeDesc(userId, type, 0)
//                    .count();
//        } else {
//            likesFlux = likeRepository.findByUserIdAndIsDeletedOrderByCreateTimeDesc(userId, 0)
//                    .skip((finalPage - 1) * finalSize)
//                    .take(finalSize)
//                    .flatMap(this::enrichLike);
//
//            countMono = likeRepository.findByUserIdAndIsDeletedOrderByCreateTimeDesc(userId, 0)
//                    .count();
//        }
//
//        return likesFlux.collectList()
//                .zipWith(countMono)
//                .map(tuple -> {
//                    List<Like> likes = tuple.getT1();
//                    Long total = tuple.getT2();
//
//                    Map<String, Object> result = new HashMap<>();
//                    result.put("records", likes);
//                    result.put("total", total);
//                    result.put("pages", (total + finalSize - 1) / finalSize);
//                    result.put("current", finalPage);
//                    return result;
//                });
//    }
//
//    @Override
//    public Mono<Map<Long, Long>> batchGetLikeCounts(Integer type, Iterable<Long> targetIds) {
//        return likeRepository.countByTypeAndTargetIdsAndIsDeleted(type, targetIds)
//                .collectMap(
//                        objects -> (Long) objects[0],  // targetId
//                        objects -> (Long) objects[1]   // count
//                )
//                .defaultIfEmpty(Collections.emptyMap());
//    }
//}