package com.hybris.environment;

import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;

import com.hybris.ConfigurationKeys;
import com.hybris.HybrisRecipe;
import com.hybris.HybrisVersion;
import com.hybris.JavaVersion;
import com.hybris.provider.Provider;


public class Environment {
	
	private String projectCode;
	private EnvironmentType environmentType;
	private Provider provider;
	public static final String SERVER_DOMAIN = ".hybrishosting.com";
	
	public Environment(Provider provider, String projectCode, EnvironmentType environmentType) {
		// TODO Auto-generated constructor stub
		this.setProvider(provider);
		this.setProjectCode(projectCode);
		this.setEnvironmentType(environmentType);
	}

	public Provider getProvider() {
		return provider;
	}

	public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public String getProjectCode() {
		return projectCode;
	}

	public void setProjectCode(String project_code) {
		if(Character.isDigit(project_code.charAt(0))){
			System.out.println("Project code should start from a letter.");
			this.projectCode=null;
			System.exit(0);
		}else if(project_code.matches("^[a-zA-Z0-9]*$")){
			this.projectCode = project_code.toLowerCase();
		}else{
			System.out.println("Special charachters are not accepteble for project code.");
			this.projectCode=null;
			System.exit(0);
		}
	}

	public EnvironmentType getEnvironmentType() {
		return environmentType;
	}

	public void setEnvironmentType(EnvironmentType environment_type) {
		this.environmentType = environment_type;
	}
	
	public Properties getConfigurationProps(HybrisVersion hybrisVersion, HybrisRecipe hybrisRecipe, JavaVersion javaVersion, String domainName){
		Properties configurationProps = new Properties();
		configurationProps.setProperty(ConfigurationKeys.hybris_version.name(), hybrisVersion.getHybrisVersion());
		configurationProps.setProperty(ConfigurationKeys.hybris_package.name(), hybrisVersion.getHybrisPackage());
		configurationProps.setProperty(ConfigurationKeys.hybris_recipe.name(), hybrisRecipe.getRecipeId());
		configurationProps.setProperty(ConfigurationKeys.java_version.name(), javaVersion.getJavaVersion());
		configurationProps.setProperty(ConfigurationKeys.java_package.name(), javaVersion.getPackageName());
		configurationProps.setProperty(ConfigurationKeys.solr_package.name(), hybrisVersion.getSolrPackage());
		configurationProps.setProperty(ConfigurationKeys.default_shop.name(), hybrisRecipe.getDefaultShop());
		configurationProps.setProperty(ConfigurationKeys.domain_name.name(), domainName);
		return configurationProps;
	}
	
	private ServerType getServerType(String hostname){
		
		String serverTypeCode = hostname.split("-")[3];
		ServerType serverType = ServerType.Admin;
		serverType = serverType.getServerType(serverTypeCode);
		return serverType;
	}
	
	private String getClusterId(String hostname){
		
		String clusterId="0";
		String hostCount=hostname.split("-")[4].substring(2, 3);
		
		ServerType serverType = this.getServerType(hostname);
		switch (serverType) {
		case Admin:
			int adminClusterId = Integer.parseInt(hostCount);
			adminClusterId -= 1;
			clusterId = Integer.toString(adminClusterId);
			break;
		case Application:
			int appClusterId = Integer.parseInt(hostCount);
			appClusterId -= 1;
			clusterId = "1" + Integer.toString(appClusterId);
			break;
		default:
			break;
		}
		
		return clusterId;
	}
	
	public String getHostName(Server server){
		String hostname = "";
		ServerType serverType = server.getServerType();
		hostname = this.projectCode + "-" + this.environmentType.getCode() + "-" + this.provider.getCode() + "-" 
				+ serverType.getCode() + "-001" + SERVER_DOMAIN;
		return hostname;
	}
	
	public HashMap<ServerType, ServerInstance> create(ComputeService computeService, Server[] servers, Properties configurationProps){
		
		HashMap<ServerType, ServerInstance> environmentMap = new HashMap<ServerType, ServerInstance>();
		
		if(servers.length == 0){
			System.out.println("Server list is empty!");
			return environmentMap;
		}
		System.out.println(">> Creating server instances ..");
		ServerInstance hybrisServerInstance = null;
		
		try {
			
			for(Server server:servers){
				
				Template template = server.getTemplate(this.getProvider(), this.getEnvironmentType());
				String hostname = this.getHostName(server);
				ServerInstance serverInstance = server.create(template, hostname);
				environmentMap.put(server.getServerType(), serverInstance);
				switch (server.getServerType()) {
				case Admin:
					configurationProps.setProperty(ConfigurationKeys.adm_host_name.name(), hostname);
					configurationProps.setProperty(ConfigurationKeys.adm_host_ip.name(), serverInstance.getNode().getPublicAddresses()
																												.iterator().next());
					break;
				case Application:
					configurationProps.setProperty(ConfigurationKeys.app_host_name.name(), hostname);
					configurationProps.setProperty(ConfigurationKeys.app_host_ip.name(), serverInstance.getNode().getPublicAddresses()
																												.iterator().next());
					break;
				case Database:
					configurationProps.setProperty(ConfigurationKeys.db_host_name.name(), hostname);
					configurationProps.setProperty(ConfigurationKeys.db_host_ip.name(), serverInstance.getNode().getPublicAddresses()
																												.iterator().next());
					break;
				case Search:
					configurationProps.setProperty(ConfigurationKeys.srch_host_name.name(), hostname);
					configurationProps.setProperty(ConfigurationKeys.srch_host_ip.name(), serverInstance.getNode().getPublicAddresses()
																												.iterator().next());
					break;
				case Web:
					configurationProps.setProperty(ConfigurationKeys.web_host_name.name(), hostname);
					configurationProps.setProperty(ConfigurationKeys.web_host_ip.name(), serverInstance.getNode().getPublicAddresses()
																												.iterator().next());
					break;
				default:
					break;
				}
				
			}
			
			System.out.println("<< Server Instances are created for " + this.projectCode + "-" + this.getEnvironmentType().getCode());
			
			if(environmentMap.keySet().contains(ServerType.Database)){
				ServerInstance dbServerInstance = environmentMap.get(ServerType.Database);
				dbServerInstance.provisionMySql(configurationProps);
			}
			
			if(environmentMap.keySet().contains(ServerType.Admin)){
				ServerInstance adminServerInstance = environmentMap.get(ServerType.Admin);
				adminServerInstance.provisionJava(configurationProps);
				String adminClusterId = this.getClusterId(adminServerInstance.getHostname());
				configurationProps.setProperty(ConfigurationKeys.cluster_id.name(), adminClusterId);
				adminServerInstance.provisionHybris(configurationProps);
				adminServerInstance.initializeDB(configurationProps);
				adminServerInstance.setupNfsServer(configurationProps);
				hybrisServerInstance = adminServerInstance;
			}
			
			if(environmentMap.keySet().contains(ServerType.Application)){
				ServerInstance appServerInstance = environmentMap.get(ServerType.Application);
				appServerInstance.provisionJava(configurationProps);
				String appClusterId = this.getClusterId(appServerInstance.getHostname());
				configurationProps.setProperty(ConfigurationKeys.cluster_id.name(), appClusterId);
				appServerInstance.provisionHybris(configurationProps);
				if(hybrisServerInstance.equals(null)){
					appServerInstance.initializeDB(configurationProps);
					hybrisServerInstance = appServerInstance;
				}else{
					
					appServerInstance.setupNfsClient(configurationProps);
					String hybrisVersion = configurationProps.getProperty(ConfigurationKeys.hybris_version.name());
					String hybrisHome = "/opt/" + hybrisVersion + "/hybris";
					hybrisServerInstance.executeCommand("cp -r " + hybrisHome + "/data/media/sys_master /var/nfs");
					appServerInstance.executeCommand("cd /mnt/nfs/var/nfs/; mv /mnt/nfs/var/nfs/sys_master/* " + hybrisHome + "/data/media");
					appServerInstance.executeCommand("chmod -R 775 " + hybrisHome + "; chown -R hybris:hybris " + hybrisHome);
				}
			}
			
			if(environmentMap.keySet().contains(ServerType.Search)){
				ServerInstance searchServerInstance = environmentMap.get(ServerType.Search);
				searchServerInstance.provisionJava(configurationProps);
				searchServerInstance.provisionSolr(configurationProps);
				hybrisServerInstance.integrateSolrOnHybris(configurationProps);
			}
			
			
			if(environmentMap.keySet().contains(ServerType.Web)){
				ServerInstance webServerInstance = environmentMap.get(ServerType.Web);
				String hybrisHost = hybrisServerInstance.getHostname();
				String hybrisIP = hybrisServerInstance.getNode().getPublicAddresses().iterator().next();
				webServerInstance.provisionWeb(configurationProps, hybrisHost, hybrisIP);
			}
			
			if(environmentMap.keySet().contains(ServerType.Admin) && environmentMap.keySet().contains(ServerType.Application)){
				ServerInstance adminServerInstance = environmentMap.get(ServerType.Admin);
				ServerInstance appServerInstance = environmentMap.get(ServerType.Application);
				adminServerInstance.executeCommand("sudo su hybris; nohup sudo service hybris start");
				appServerInstance.executeCommand("sudo su hybris; nohup sudo service hybris start");
			}else{
				hybrisServerInstance.integrateSolrOnHybris(configurationProps);
				hybrisServerInstance.executeCommand("sudo su hybris; nohup sudo service hybris start");
			}
			
			System.out.println(configurationProps);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return environmentMap;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args){
		
		long timeStart = System.currentTimeMillis();
		try{
			
			Provider provider = Provider.AmazonWebService;
			ComputeService computeService = provider.getComputeService();
			Server[] servers = {new Server(computeService, ServerType.Admin),
								new Server(computeService, ServerType.Application),
								new Server(computeService, ServerType.Web),
								new Server(computeService, ServerType.Search),
								new Server(computeService, ServerType.Database)};
			String projectCode="hybris62b2c";
			Environment environment = new Environment(provider, projectCode, EnvironmentType.Development);
			Properties configurationProps = environment.getConfigurationProps(HybrisVersion.Hybris6_2_0, 
																			  HybrisRecipe.B2C_Accelerator, 
																			  JavaVersion.Java8u131, 
																			  "www." + projectCode + provider.getCode() + "demo.com");
			environment.create(computeService, servers, configurationProps);
			System.out.println(environment.getHostName(servers[4]));
			computeService.getContext().close();
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
		long timeEnd = System.currentTimeMillis();
		long duration = timeEnd - timeStart;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
		System.out.println("Time utilised for execution: " + minutes + " minutes");
	}
	
}