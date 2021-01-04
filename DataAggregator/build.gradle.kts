repositories {
	maven(url = "https://maven.pkg.github.com/coinapi/coinapi-sdk")
	{
		credentials.run()
		{
			username = "NightEule5"
			password = "1cac22e065e47f42cb10c3d5e806600ed5cbbcaf"
		}
	}
}

dependencies {
	//implementation("io.coinapi.rest:v1:1.13")
}