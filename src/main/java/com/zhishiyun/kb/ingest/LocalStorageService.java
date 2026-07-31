package com.zhishiyun.kb.ingest;

import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 本地文件存储：按 UUID 落盘，返回 storageKey。 */
@Service
public class LocalStorageService {

    @Value("${kb.storage.local-dir:data/storage}")
    private String localDir;

    /** 保存上传文件，返回 storageKey（UUID + 扩展名）。 */
    public String save(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        String key = UUID.randomUUID().toString() + ext;
        try {
            Path dir = Paths.get(localDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(key);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "保存文件失败");
        }
    }

    /** 按 storageKey 定位本地文件。 */
    public File getFile(String key) {
        return Paths.get(localDir, key).toFile();
    }

    /** 从原始文件名取扩展名，缺省为 .pdf。 */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".pdf";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
