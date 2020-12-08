#!/usr/bin/env bash

if [ -d "tmp" ]; then
  rm -rf tmp
fi

name=oxy-service
playSecret=$(head -c 32 /dev/urandom | base64)

echo "Building ${name}"

mkdir tmp
unzip -q ../target/universal/package.zip -d tmp

pip3 install -r requirements.txt &&
 ./get-build-info.py --repo .. --info --with-date > tmp/conf/build-info.txt
cp Dockerfile tmp

cat << EOF > tmp/run
#!/usr/bin/env bash
/opt/oystr/service/bin/run -Dconfig.file=/opt/oystr/service/shared/conf/local.conf -Dplay.http.secret.key="${playSecret}"
EOF

chmod +x tmp/run
cd tmp || exit

docker build -t "${name}":v1.0.0 .
