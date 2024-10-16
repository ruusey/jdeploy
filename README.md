# jdeploy
## EZ java deploy to most Linux based distros

## Usage
To use jdeploy simply run the target jar with the following parameters:

### `java -jar ./jdeploy.jar -u {REMOTE_USER} -p {PORT} -h {REMOTE_HOST} -t {TARRGET_FILE} -s {SOURCE_FILE} -i {RSA_PRIVATE_KEY} -y {REMOTE_USER_PASSWORD}`

## Program Arguments Explained
* -u Remote user to log in with
* -p Remote port to connect over - usually 22
* -h Remote host to connect to
* -t Tarrget file path on the remote machine
* -s Source local file to upload to the remote machine
* -i (optional) RSA private key path
* -y (optional) Remote user password
