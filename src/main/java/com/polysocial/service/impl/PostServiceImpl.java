package com.polysocial.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.polysocial.dto.CommentResponseDTO;
import com.polysocial.dto.ListPostDTO;
import com.polysocial.dto.PostDTO;
import com.polysocial.dto.PostFileResponseDTO;
import com.polysocial.dto.ResponseDTO;
import com.polysocial.dto.PostResponseDTO;
import com.polysocial.entity.Comments;
import com.polysocial.entity.FileUpload;
import com.polysocial.entity.Likes;
import com.polysocial.entity.PostFile;
import com.polysocial.entity.Posts;
import com.polysocial.exception.PolySocialErrorCode;
import com.polysocial.exception.PolySocialException;
import com.polysocial.repository.CommentRepository;
import com.polysocial.repository.LikeRepository;
import com.polysocial.repository.PostFileRepository;
import com.polysocial.repository.PostRepository;
import com.polysocial.service.PostService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.aspectj.apache.bcel.generic.RET;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    PostFileRepository postFileRepository;

    @Autowired
    private Cloudinary cloudinary;

    @Override
    public ListPostDTO findAllPage(Integer page, Integer limit) {
        ListPostDTO listPostDTO = new ListPostDTO();
        Pageable pageable = PageRequest.of(page, limit);
        Page<Posts> pagePost = this.postRepository.findAll(pageable);
        List<Posts> listPost = pagePost.getContent();

        if (listPost.size() > 0) {
            List<PostResponseDTO> listPostConver = new ArrayList<>();
            for (Posts post : listPost) {
                List<Comments> cm = commentRepository.findByPostId(post.getPostId());
                List<PostFile> pf = postFileRepository.findByPostId(post.getPostId());
             
                PostResponseDTO dto = modelMapper.map(post, PostResponseDTO.class);
                
                List<CommentResponseDTO> listCommentConver = new ArrayList<>();
                
                for (Comments comments : cm) {
                    CommentResponseDTO cmt = modelMapper.map(comments, CommentResponseDTO.class);
                    listCommentConver.add(cmt);
                }
                List<PostFileResponseDTO> listUrl = new ArrayList<>();
                for (PostFile postFile : pf) {
                    PostFileResponseDTO pfres = modelMapper.map(postFile, PostFileResponseDTO.class);
                    listUrl.add(pfres);
                }
                dto.setListUrl(listUrl);
                Long countLike = likeRepository.countLike(post.getPostId());
                Long countComment = commentRepository.countComment(post.getPostId());
                dto.setListComment(listCommentConver);
//                if(countLike==0) {
//                   duoc thich boi 
//                }
                dto.setCountLike(countLike);
                dto.setCountComment(countComment);
                dto.setStatus(post.getStatus());
                listPostConver.add(dto);

            }
            listPostDTO.setListPostDTO(listPostConver);

        }
        listPostDTO.setTotalPage((int) Math.ceil((double) (pagePost.getTotalElements()) / limit));
        listPostDTO.setTotalItem((int) pagePost.getTotalElements());
        listPostDTO.setPage(page);
        return listPostDTO;
    }

    String url = "";
    String type = "";

    @Override
    public PostDTO save(PostDTO dto) {
        try {
            Posts post = modelMapper.map(dto, Posts.class);
            post.setCreatedDate(LocalDateTime.now());
            post.setStatus(true);
            Posts entity = postRepository.save(post);
            
            PostFile file = new PostFile();

            file.setPostId(entity.getPostId());
            file.setUrl(url);
            file.setType(type);
            file.setIsActive(true);
            file.setCreatedDate(LocalDateTime.now());
            postFileRepository.save(file);
            return dto;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<String> saveFile(List<MultipartFile> fi) throws IOException {
        List<String> urlPath = new ArrayList();
        File folder = new File("Files");
        try {
            folder.mkdir();
            saveLocal(fi);
            urlPath = upLoadServer(fi);
            deleteFile();
        } catch (Exception e) {
            System.out.println("do day");
            this.url = "";
            this.type = "";
            folder.delete();
            return urlPath;
        }
        return urlPath;
    }

    public void saveLocal(List<MultipartFile> fi) throws IOException {
        for (int i = 0; i < fi.size(); i++) {
            FileUpload file = new FileUpload();
            file.setFile(fi.get(i));
            File f = new File(fi.get(i).getOriginalFilename());
            String type = fi.get(i).getContentType(); // check type luc up len server

            Path uploadPath = Paths.get("Files"); // trỏ toi folder
            String fName = f.getName(); // lay ra ten file
            try (InputStream inputStream = fi.get(i).getInputStream()) {
                Path filePath = uploadPath.resolve(fName);
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING); // luu file local
            } catch (IOException ioe) {
                return;
            }
        }
    }

    public List<String> upLoadServer(List<MultipartFile> fi) {
        List<String> urlPath = new ArrayList<>();
        String type = ""; // check type luc up len server
        int firtsIndex = 0;
        int lastIndex = 0;
        String url = "";
        for (int i = 0; i < fi.size(); i++) {
            type = fi.get(0).getContentType(); // check type luc up len server
            String fileName = fi.get(i).getOriginalFilename();
            try {
                if (type.equals("jpg") || type.equals("png") || type.equals("jpeg")) {
                    String json = "" + this.cloudinary.uploader().upload("./Files/" + fileName,
                            ObjectUtils.asMap("moderation", "aws_rek"));
                    firtsIndex = json.indexOf("url=");
                    lastIndex = json.indexOf("created_at");
                    url = json.substring(firtsIndex + 4, lastIndex - 2);
                    urlPath.add(url); // lay ra duong dan anh
                    // cac loai anh bi cam
                    String[] typeImage = { "Explicit Nudity", "Suggestive", "Violence", "Visually Disturbing",
                            "Rude Gesture", "Drugs", "Tobacco", "Alcohol", "Gambling", "Hate Symbols" };
                    for (int j = 0; j < type.length(); j++) {
                        if (json.contains(typeImage[j])) {
                            System.out.println("Anh khong hop le !!!!");
                            return null;
                        }
                    }
                } else {
                    String json = "" + this.cloudinary.uploader().upload("./Files/" + fileName,
                            ObjectUtils.asMap("resource_type", "auto"));
                    if (type.equals("video")) {
                        firtsIndex = json.lastIndexOf("url=");
                        lastIndex = json.indexOf("tags=");

                        System.out.println("day-->" + json);
                        url = json.substring(firtsIndex + 4, lastIndex - 2);
                        String types = url.substring(url.lastIndexOf(".") + 1);
                        System.out.println("url" + url);
                        this.type = types;
                        this.url = url;
                    } else {
                        firtsIndex = json.indexOf("url=");
                        lastIndex = json.indexOf("created_at");
                        System.out.println("day-->" + json);
                        url = json.substring(firtsIndex + 4, lastIndex - 2);
                        System.out.println("url" + url);
                        String types = url.substring(url.lastIndexOf(".") + 1);
                        this.type = types;
                        this.url = url;
                    }
                    urlPath.add(url); // lay ra duong dan anh
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return urlPath;
    }

    void deleteFile() {
        Path filePath = Paths.get("Files");
        if (filePath.toFile().exists()) {
            File[] files = filePath.toFile().listFiles();
            for (File file : files) {
                file.delete();
            }
        }
    }
    @Override
	public PostDTO findById(Long postId) {
    	try {
    		Optional<Posts> postById = this.postRepository.findById(postId);
        	if(postById.isPresent()) {
        		throw new Exception();
        	}
        	PostDTO dto = modelMapper.map(postById, PostDTO.class);
        	return dto;
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return null;
    }
//    public Long deleteByIdPost(Long postId) {
//    	try {
//    	  	Optional<Posts> postById = this.postRepository.findById(postId);
//        	if(postById.isPresent()) {
//        		throw new Exception();
//        	}
//        	Posts post = postById.get();
//        	if(post.getStatus()==false) {
//        		throw new Exception();
//        	}
//        	post.setStatus(false);
//        	
//        	this.postRepository.save(post);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//        return null;
//    }

}
