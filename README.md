# jdeploy
## EZ java deploy to most Linux based distros

## Usage
To use jdeploy simply run the target jar with the following parameters:

### `java -jar ./jdeploy.jar -u {REMOTE_USER} -p {PORT} -h {REMOTE_HOST} -t {TARRGET_FILE} -s {SOURCE_FILE} -i {RSA_PRIVATE_KEY} -y {REMOTE_USER_PASSWORD}`

## Program Arguments Explained
* -u Remote user to log in with
* -p Remote port to connect over - usually 22
* -h Remote host to connect to
* -t Target file path on the remote machine
* -s Source local file to upload to the remote machine
* -i (optional) RSA private key path
* -y (optional) Remote user password

### Example
`java -jar ./jdeploy.jar -u ec2-user -p 22 -h 99.117.127.255 -t /home/ec2-user/service/myjar.jar -s /home/myuser/Development/GitRepositories/myproject/target/myjar.jar -i /home/myuser/Development/Keys/deployment-key.rsa`
