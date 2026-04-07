cd ui
pnpm install
pnpm build
cd ..
mvn clean compile exec:exec
