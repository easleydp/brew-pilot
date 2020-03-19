package com.easleydp.tempctrl.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.ArduinoChamberManager;
import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.DummyChamberManager;
import com.easleydp.tempctrl.domain.PropertyUtils;

@SpringBootApplication(
     // Uncomment this line to temporarily disable Spring Security:
// exclude = { SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class }
)
@EnableScheduling
public class TempctrlApplication
{
    private static final Logger logger = LoggerFactory.getLogger(TempctrlApplication.class);

    @Autowired
    private Environment env;

    @PostConstruct
    public void init()
    {
        PropertyUtils.setEnv(env);
    }

    @Bean
    public Path dataDir()
    {
        String strPath = env.getRequiredProperty("dataDir");
        Path path = Paths.get(strPath);
        Assert.state(Files.exists(path), "dataDir '" + strPath + "' does not exist.");
        return path;
    }

    @Bean
    public ChamberRepository chamberRepository(Path dataDir)
    {
        return new ChamberRepository(dataDir);
    }

    @Bean
    public ChamberManager chamberManager(ChamberRepository chamberRepository)
    {
    	boolean useDummyChamberManager = PropertyUtils.getBoolean("dummy.chambers", false);
    	logger.info("Using " + (useDummyChamberManager ? "DummyChamberManager" : "ArduinoChamberManager"));
        return useDummyChamberManager ?
                    new DummyChamberManager(chamberRepository) :
                    new ArduinoChamberManager(chamberRepository);
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
