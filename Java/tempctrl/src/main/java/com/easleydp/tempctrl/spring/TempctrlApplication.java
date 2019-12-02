package com.easleydp.tempctrl.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.ArduinoChamberManager;
import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.Chambers;

@SpringBootApplication
@EnableScheduling
public class TempctrlApplication {
    @Autowired
    private Environment env;

    @Bean
    public Path dataDir(Environment env)
    {
        String strPath = env.getRequiredProperty("dataDir");
        Path path = Paths.get(strPath);
        Assert.state(Files.exists(path), "dataDir '" + strPath + "' does not exist.");
        return path;
    }

//    @Bean
//    public Collection<Chamber> chambers()
//    {
//        return new ConcurrentLinkedQueue<Chamber>();
//    }

    @Bean
    public Chambers chambers(Path dataDir)
    {
        return new Chambers(dataDir);
    }

    @Bean
    public ChamberManager chamberManager()
    {
        return new ArduinoChamberManager();
    }
//
//    @Bean
//    public ReadingsCollector readingsCollector(ChamberManager chamberManager, Path dataDir)
//    {
//        return new ReadingsCollector(chamberManager, dataDir);
//    }


	public static void main(String[] args) {
		SpringApplication.run(TempctrlApplication.class, args);
	}

}
