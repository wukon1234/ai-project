package com.zhishiyun.kb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 智识云后端启动类。 */
@SpringBootApplication
@MapperScan("com.zhishiyun.kb.mapper")
public class KbApplication {

    public static void main(String[] args) {

        SpringApplication.run(KbApplication.class, args);
        System.out.println("智识云后端启动成功！");
    }
}
