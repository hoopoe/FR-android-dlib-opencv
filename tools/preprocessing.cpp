#include <iostream>
#include <dlib/dnn.h>
#include <dlib/gui_widgets.h>
#include <dlib/clustering.h>
#include <dlib/string.h>
#include <dlib/image_io.h>
#include <dlib/image_processing/frontal_face_detector.h>
#include <experimental/filesystem>

using namespace dlib;
using namespace std;

template <template <int, template<typename>class, int, typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual = add_prev1<block<N, BN, 1, tag1<SUBNET>>>;

template <template <int, template<typename>class, int, typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual_down = add_prev2<avg_pool<2, 2, 2, 2, skip1<tag2<block<N, BN, 2, tag1<SUBNET>>>>>>;

template <int N, template <typename> class BN, int stride, typename SUBNET>
using block = BN<con<N, 3, 3, 1, 1, relu<BN<con<N, 3, 3, stride, stride, SUBNET>>>>>;

template <int N, typename SUBNET> using ares = relu<residual<block, N, affine, SUBNET>>;
template <int N, typename SUBNET> using ares_down = relu<residual_down<block, N, affine, SUBNET>>;

template <typename SUBNET> using alevel0 = ares_down<256, SUBNET>;
template <typename SUBNET> using alevel1 = ares<256, ares<256, ares_down<256, SUBNET>>>;
template <typename SUBNET> using alevel2 = ares<128, ares<128, ares_down<128, SUBNET>>>;
template <typename SUBNET> using alevel3 = ares<64, ares<64, ares<64, ares_down<64, SUBNET>>>>;
template <typename SUBNET> using alevel4 = ares<32, ares<32, ares<32, SUBNET>>>;

using anet_type = loss_metric<fc_no_bias<128, avg_pool_everything<
    alevel0<
    alevel1<
    alevel2<
    alevel3<
    alevel4<
    max_pool<3, 3, 2, 2, relu<affine<con<32, 7, 7, 2, 2,
    input_rgb_image_sized<150>
    >>>>>>>>>>>>;


namespace filesys = std::experimental::filesystem;
std::vector<std::string> getAllFilesInDir(const std::string &dirPath, const std::vector<std::string> acceptExt = {})
{
    std::vector<std::string> listOfFiles;
    try {
        if (filesys::exists(dirPath) && filesys::is_directory(dirPath))
        {
            filesys::recursive_directory_iterator iter(dirPath);
            filesys::recursive_directory_iterator end;
            while (iter != end)
            {
                if (filesys::is_regular_file(iter->path()) &&
                    (std::find(acceptExt.begin(), acceptExt.end(), iter->path().extension()) != acceptExt.end()))
                {
                    listOfFiles.push_back(iter->path().string());
                }
                error_code ec;
                iter.increment(ec);
                if (ec) {
                    std::cerr << "Error While Accessing : " << iter->path().string() << " :: " << ec.message() << '\n';
                }
            }
        }
    }
    catch (std::system_error & e)
    {
        std::cerr << "Exception :: " << e.what();
    }
    return listOfFiles;
}

void buildClusters(std::string sourceDir, std::string chippedDir)
{
    frontal_face_detector detector = get_frontal_face_detector();
    shape_predictor sp;
    deserialize("shape_predictor_5_face_landmarks.dat") >> sp;
    anet_type net;
    deserialize("dlib_face_recognition_resnet_model_v1.dat") >> net;

    std::vector<std::string> listOfFiles = getAllFilesInDir(sourceDir, { ".jpg", ".JPG", ".png", ".PNG" });
    std::vector<matrix<rgb_pixel>> faces;

    for (auto str : listOfFiles) {
        std::cout << str << std::endl;
        matrix<rgb_pixel> img;
        load_image(img, str);

        for (auto face : detector(img))
        {
            auto shape = sp(img, face);
            matrix<rgb_pixel> face_chip;
            extract_image_chip(img, get_face_chip_details(shape, 150, 0.25), face_chip);
            faces.push_back(move(face_chip));
        }
    }
    if (faces.size() == 0)
    {
        cout << "No faces found in image!" << endl;
    }

    std::vector<matrix<float, 0, 1>> face_descriptors = net(faces);

    std::vector<sample_pair> edges;
    for (size_t i = 0; i < face_descriptors.size(); ++i)
    {
        for (size_t j = i; j < face_descriptors.size(); ++j)
        {
            if (length(face_descriptors[i] - face_descriptors[j]) < 0.5) {
                edges.push_back(sample_pair(i, j));
            }
        }
    }
    std::vector<unsigned long> labels;
    const auto num_clusters = chinese_whispers(edges, labels);
    cout << "number of people found in the image: " << num_clusters << endl;

    for (size_t cluster_id = 0; cluster_id < num_clusters; ++cluster_id)
    {
        std::vector<matrix<rgb_pixel>> temp;
        for (size_t j = 0; j < labels.size(); ++j)
        {
            if (cluster_id == labels[j]) {
                temp.push_back(faces[j]);
            }
        }
        std::string filename = chippedDir + "/" + cast_to_string(cluster_id) + ".png";
        save_png(tile_images(temp), filename);
    }
}

int main(int argc, char** argv)
{
    if (argc != 3)
    {
        cout << "Run this example by invoking it like this: " << endl;
        cout << "   ./preprocess folder_with_images out_folder" << endl;
        return 1;
    } 
    buildClusters(argv[1], argv[2]);
    return 0;
}