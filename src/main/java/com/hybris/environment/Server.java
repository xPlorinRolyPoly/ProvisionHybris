package com.hybris.environment;

import java.io.File;
import java.util.ArrayList;

import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.googlecomputeengine.compute.options.GoogleComputeEngineTemplateOptions;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.hybris.provider.Provider;

public class Server {

	private ServerType serverType;
	private ComputeService computeservice;
	public static final String SERVER_DOMAIN=".hybrishosting.com";
	
	public Server(ComputeService computeService, ServerType type) {
		// TODO Auto-generated constructor stub
		this.setComputeservice(computeService);
		this.setServerType(type);
	}
	
	public ServerType getServerType() {
		return serverType;
	}
	public void setServerType(ServerType serverType) {
		this.serverType = serverType;
	}

	public ComputeService getComputeservice() {
		return computeservice;
	}

	public void setComputeservice(ComputeService computeservice) {
		this.computeservice = computeservice;
	}
	
	public Template getTemplate(Provider provider, EnvironmentType environmentType){
		
		Template template = null;
		String keyName = "alpanachaphalkar";
		try{
		switch (provider) {
			case AmazonWebService:
				
				String awsHardwareId = "t2.large";
				String awsImageId = "us-east-1/ami-cd0f5cb6";
				String awsSubnetId = "subnet-13d3fb5b";
				String awsSecuritygroupId = "sg-8651acf6";
				String awsDeviceName = "/dev/sda1";
				TemplateBuilder awsTemplateBuilder = this.computeservice.templateBuilder().locationId(provider.getRegion().getID())
																				  .imageId(awsImageId)
																				  .hardwareId(awsHardwareId);
				template = awsTemplateBuilder.build();
				TemplateOptions awsTemplateOptions = template.getOptions();
				awsTemplateOptions.as(AWSEC2TemplateOptions.class).keyPair(keyName)
																.subnetId(awsSubnetId)
																.securityGroups(awsSecuritygroupId)
																.mapNewVolumeToDeviceName(awsDeviceName, 40, true);
				
				break;
			case GoogleCloudProvider:
				String pathToKey = "C:\\cygwin64\\home\\D066624\\.ssh\\id_rsa.pub";
				String GcePublicKey = Files.toString(new File(pathToKey), Charsets.UTF_8);
				String gceHardwareId = "";
				if(this.serverType.equals(ServerType.Admin) || this.serverType.equals(ServerType.Application)){
					gceHardwareId = "https://www.googleapis.com/compute/v1/projects/provisionhybris/zones/us-east1-b/machineTypes/n1-standard-2";
				}else{
					gceHardwareId = "https://www.googleapis.com/compute/v1/projects/provisionhybris/zones/us-east1-b/machineTypes/n1-standard-1";
				}
				String gceImageId = "https://www.googleapis.com/compute/v1/projects/provisionhybris/global/images/hybris-dev-image";
				String gceSecurityGroupId = "demo-hybris-firewall";
				ArrayList<String> tags = new ArrayList<String>();
				tags.add(gceSecurityGroupId);
				TemplateBuilder gceTemplateBuilder = this.computeservice.templateBuilder().locationId(provider.getRegion().getID())
																							.os64Bit(true)
																							.imageId(gceImageId)
																							.hardwareId(gceHardwareId);
				template = gceTemplateBuilder.build();
				TemplateOptions gceTemplateOptions = template.getOptions();
				gceTemplateOptions.as(GoogleComputeEngineTemplateOptions.class).autoCreateKeyPair(false)
																				.userMetadata("ssh-keys", GcePublicKey)
																				.tags(tags);
				break;
			/*case MicrosoftAzure:
				String azuImageId = "b39f27a8b8c64d52b05eac6a62ebad85__Ubuntu-16_04-LTS-amd64-server-20161221-en-us-30GB/Central US";
				String azuHardwareId = "";
				if(this.serverType.equals(ServerType.Admin) || this.serverType.equals(ServerType.Application)){
					azuHardwareId = "STANDARD_D2";
				}else{
					azuHardwareId = "STANDARD_D1";
				}
				TemplateBuilder azureTemplateBuilder = this.computeservice.templateBuilder().locationId(provider.getRegion().getID())
				                                                                            .os64Bit(true)
				                                                                            .imageId(azuImageId)
				                                                                            .hardwareId(azuHardwareId);
				template = azureTemplateBuilder.build();
				TemplateOptions azuTemplateOptions = template.getOptions();
				break;*/
			default:
				break;
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return template;
		
	}
	
	public ServerInstance create(Template template, String hostname) throws Exception{
		System.out.println();
		System.out.println(">> Creating instance " + hostname);
		String host = hostname.replace(SERVER_DOMAIN, "");
		NodeMetadata instance = Iterables.getOnlyElement(this.computeservice.createNodesInGroup(host, 1, template));
		System.out.println("<<	Server " + hostname + " is created with following details: ");
		System.out.println("	Name: " + instance.getHostname());
		System.out.println("	ID: " + instance.getId());
		System.out.println("	Private IP: " + instance.getPrivateAddresses());
		System.out.println("	Public IP: " + instance.getPublicAddresses());
		ServerInstance serverInstance = new ServerInstance(this.computeservice, instance.getId(), hostname);
		System.out.println(">> Setting hostname");
		serverInstance.executeCommand("hostnamectl set-hostname " + hostname + "; echo \"127.0.0.1 `hostname`\" >>/etc/hosts");
		System.out.println("<< Instance is created with hostname " + hostname);
		System.out.println();
		return serverInstance;
	}
	
}
