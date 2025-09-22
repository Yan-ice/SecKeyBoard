genpem:
	openssl genrsa -out private_key.pem 2048
	openssl req -x509 -new -nodes -key private_key.pem -sha256 -days 365 -out test_cert.pem \
	    -subj "/C=CN/ST=Shanghai/L=Shanghai/O=TestCompany/OU=Dev/CN=example.com" \
	    -addext "reqformat = number" \
	    -addext "nonce = 12448" 
	openssl x509 -in test_cert.pem -outform der -out test_cert.der

