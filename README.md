# layermaker-maven-plugin
## Description
Zips up all your project's provided dependencies and the transitive dependencies of those provided dependencies.  It puts the files under /java/lib in the zip and adds a Manifest.  It attaches the zip file to the deployment artifacts so that the zip is installed to you local repo or deployed to a remote repo.  The zip has the same group and artifiact ids, but adds a classifier of `layer`.

## Use
Add the following to you pom file:
```xml
<plugin>
    <groupId>com.nufrof</groupId>
    <artifactId>aws-layermaker-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>create-layer</goal>
            </goals>
        </execution>
    </executions>
</plugin>

```

## Useful GPG commands
```shell
# Handle Agent
gpgconf --kill gpg-agent
gpgconf --kill dirmngr
gpgconf --launch gpg-agent
gpgconf --launch dirmngr
gpgconf --reload gpg-agent

# Create keys
gpg --gen-key

# Get keys
gpg --list-signatures --keyid-format 0xshort
```