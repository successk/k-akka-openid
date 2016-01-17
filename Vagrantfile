Vagrant.configure(2) do |config|
  config.vm.box = "terrywang/archlinux"
  config.vm.provision :shell, path: "vagrantbootstrap.sh"
  config.vm.network :forwarded_port, guest: 9000, host: 19000
  config.vm.provider "virtualbox" do |v|
    v.memory = 1024
  end
end
